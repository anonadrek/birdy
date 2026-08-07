package se.birdy.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import se.birdy.app.App
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.app.badges.BadgeCatalogLoader
import se.birdy.app.bootstrap.SharedPrefsBadgeVersionStore
import se.birdy.app.di.AppGraph
import se.birdy.app.i18n.LocaleResolver
import se.birdy.app.i18n.toLocaleTagOrNull
import se.birdy.app.notifications.workers.TrophyProgressWorker
import se.birdy.app.photo.PhotoStorageProvider
import se.birdy.app.ui.audio.AndroidAudioRecorderAdapter
import se.birdy.app.ui.audio.AndroidWaveformRenderer
import se.birdy.app.ui.badges.BadgeStringMap
import se.birdy.app.ui.debug.DiagnosticsRunner
import se.birdy.app.ui.debug.DiagnosticsScreen
import se.birdy.app.ui.settings.AppLocaleApplier
import se.birdy.app.ui.settings.SettingsLauncherSetup
import se.birdy.app.usecase.ExportJournalUseCase
import se.birdy.data.DatabaseFactory
import se.birdy.data.badge.BadgeRepositoryImpl
import se.birdy.data.db.BirdyData
import se.birdy.data.observation.SqlDelightObservationRepository
import se.birdy.datastore.UserPreferences
import se.birdy.datastore.UserPreferencesStore
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import se.birdy.ml.AndroidTfliteAudioRunner
import se.birdy.ml.AndroidTfliteRunner
import se.birdy.ml.AudioClassifierFactory
import se.birdy.ml.AudioClassifierMode
import se.birdy.ml.BirdAudioClassifier
import se.birdy.ml.BirdClassifier
import se.birdy.ml.BirdClassifierFactory
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.ClassifierBootstrapState
import se.birdy.ml.ClassifierMode
import se.birdy.ml.FakeAudioClassifier
import se.birdy.ml.FakeBirdClassifier
import se.birdy.ml.ImagePreprocessor
import se.birdy.ml.ModelArtifactProvider
import se.birdy.ml.TfLiteBirdClassifier
import se.birdy.ml.camera.AndroidCameraSource
import se.birdy.ml.loadAiyLabelMapper
import se.birdy.ml.loadModelMetadata
import se.birdy.pdf.JournalPdfRenderer
import se.birdy.pdf.PdfFontProvider
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import android.graphics.Color as AndroidColor

private const val UPGRADE_INSTALL_BACKDATE_MS = 8L * 24 * 60 * 60 * 1000

@Suppress("TooManyFunctions")
class MainActivity : AppCompatActivity() {
    private lateinit var appGraph: AppGraph
    private lateinit var billingClient: se.birdy.app.data.premium.PremiumBillingClient

    private val deepLinkFlow =
        kotlinx.coroutines.flow.MutableSharedFlow<String>(
            replay = 1,
            extraBufferCapacity = 4,
        )

    private val requestPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            lifecycleScope.launch {
                appGraph.userPreferences.setPushPermissionAsked(true)
                if (granted) {
                    appGraph.notificationScheduler?.scheduleDailyBird()
                    appGraph.notificationScheduler?.scheduleWeeklyRecap()
                    appGraph.notificationScheduler?.scheduleTrophyProgress()
                    appGraph.notificationScheduler?.cancelStreakRiskCheck()
                }
            }
        }

    // Location capture stays graceful whether or not permission is granted, so no result handling is needed.
    private val requestLocationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Older Android grants at install time. Just persist + schedule.
            lifecycleScope.launch {
                appGraph.userPreferences.setPushPermissionAsked(true)
                appGraph.notificationScheduler?.scheduleDailyBird()
                appGraph.notificationScheduler?.scheduleWeeklyRecap()
                appGraph.notificationScheduler?.scheduleTrophyProgress()
                appGraph.notificationScheduler?.cancelStreakRiskCheck()
            }
        }
    }

    /**
     * Caches the in-flight or completed audio-classifier [Deferred] so the 57 MB TFLite
     * model is loaded at most once per session. [AtomicReference] + CAS ensures only one
     * coroutine wins the load race; losers use [CoroutineStart.LAZY] so their Deferred
     * body never runs and no native handle is created (Fix #2).
     */
    private val audioBootstrapCache =
        AtomicReference<Deferred<Pair<BirdAudioClassifier, AudioClassifierMode>>?>(null)

    /**
     * Suspend lambda passed to [AppGraph.audioClassifierProvider].
     *
     * Returns a [Pair] of classifier + mode so the UI can show a DEMO banner when
     * the real model failed to load (Fix #4).
     *
     * Fix #2: Uses [CoroutineStart.LAZY] so the losing branch's Deferred body never
     * executes — no interpreter is constructed, no native handle can leak.
     *
     * Fix #5: Retry loop re-checks the cache reference after [Deferred.await] returns and
     * loops to build a fresh instance if it changed underneath us, rather than returning a
     * stale one. Nothing clears [audioBootstrapCache] today — the onTrimMemory-based release
     * was removed (i2c ownership rule: the Activity must not close a classifier a live
     * ViewModel still holds) — but the guard is harmless and stays as defense-in-depth
     * against a future cache-clear.
     *
     * Fix #8: A [Deferred] that completed with an exception (e.g. a release-build
     * [buildAudioClassifier] failure, `allowFallback=false`) is evicted from
     * [audioBootstrapCache] before the exception propagates, so the next call — e.g. after
     * the user taps "Try again" — rebuilds via the normal CAS-from-null path above instead of
     * re-awaiting the same permanently-failed load forever.
     */
    private val audioProvider: suspend () -> Pair<BirdAudioClassifier, AudioClassifierMode> =
        audioProvider@{
            while (true) {
                val cached = audioBootstrapCache.get()
                val deferred: Deferred<Pair<BirdAudioClassifier, AudioClassifierMode>> =
                    if (cached != null) {
                        cached
                    } else {
                        val newDeferred =
                            lifecycleScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                                buildAudioClassifier()
                            }
                        if (audioBootstrapCache.compareAndSet(null, newDeferred)) {
                            newDeferred
                        } else {
                            // Lost CAS race. LAZY means our newDeferred body never started —
                            // no native handle to leak. Use the winner's Deferred instead.
                            audioBootstrapCache.get() ?: continue
                        }
                    }
                val result =
                    try {
                        deferred.await()
                    } catch (t: Throwable) {
                        // Evict only when the DEFERRED ITSELF failed — a caller-side
                        // cancellation (CE while the shared load is still running/healthy)
                        // must not evict a healthy in-flight/completed load (Fix #8). CAS on
                        // the exact reference so a concurrent newer cache entry is never
                        // clobbered; the next tap after eviction rebuilds via the normal
                        // CAS-from-null path above.
                        @OptIn(ExperimentalCoroutinesApi::class)
                        val deferredFailed = deferred.isCompleted && deferred.getCompletionExceptionOrNull() != null
                        if (deferredFailed) {
                            audioBootstrapCache.compareAndSet(deferred, null)
                        }
                        throw t
                    }
                // Re-check: if the cache reference changed while we awaited, the previous
                // classifier may have been closed. Loop to build a fresh one (Fix #5).
                if (audioBootstrapCache.get() === deferred) {
                    return@audioProvider result
                }
                // Cache reference changed — loop and resolve the new one.
            }
            @Suppress("UNREACHABLE_CODE")
            error("audioProvider loop exited unexpectedly")
        }

    /**
     * Builds the audio classifier via [AudioClassifierFactory]. Extracted from [audioProvider]
     * to keep the lambda readable.
     *
     * `allowFallback = BuildConfig.DEBUG`: debug builds keep the historical fallback-to-
     * [FakeAudioClassifier] behavior; release builds propagate a real-model load failure
     * honestly (surfaced by the ViewModel as a bootstrap-failed error + retry) instead of
     * silently answering every recording with a canned guess forever — see the 16 KB-device
     * classifier-load bug. Crashlytics integration is deferred to Plan 6b3.
     */
    private suspend fun buildAudioClassifier(): Pair<BirdAudioClassifier, AudioClassifierMode> =
        AudioClassifierFactory(
            createReal = { AndroidTfliteAudioRunner.load(applicationContext) },
            createFallback = { FakeAudioClassifier() },
            onDegrade = { t ->
                android.util.Log.w("Birdy", "Audio TFLite init failed", t)
                println("Birdy/audio: classifier degrade: ${t.message}")
            },
            allowFallback = BuildConfig.DEBUG,
        ).create()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Edge-to-edge: BottomNavBar's paper background flows beneath the
        // system nav bar so the two surfaces fuse into a single strip.
        // BottomNavBar applies windowInsetsPadding(navigationBars) to keep
        // its tab icons above the system nav.
        enableEdgeToEdge(
            navigationBarStyle =
                SystemBarStyle.light(
                    AndroidColor.TRANSPARENT,
                    AndroidColor.TRANSPARENT,
                ),
        )
        super.onCreate(savedInstanceState)
        cleanOldCacheFrames()
        SpeciesRepositoryProvider.init(applicationContext)
        PhotoStorageProvider.init(applicationContext)
        AppLocaleApplier.init(applicationContext)
        SettingsLauncherSetup.init(applicationContext)
        // Build graph before setContent so recomposition cannot orphan
        // ClassifierBootstrap or leak the TFLite Interpreter.
        appGraph = buildAppGraph()
        se.birdy.app.AndroidAppGraphHolder
            .set(appGraph)
        // Plan 6b3 T19: unlock premium_field_member badge + welcome-sheet
        // enqueue whenever effectivePremiumActive flips false→true (cancelled with lifecycleScope).
        appGraph.premiumActivationListener.start(lifecycleScope)
        setContent { App(appGraph) }
        intent?.let { handleDeepLink(it) }
        lifecycleScope.launch {
            val prefs = appGraph.userPreferences
            val notificationsOn =
                NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
            if (notificationsOn) {
                if (prefs.dailyBirdPushEnabled.first()) {
                    appGraph.notificationScheduler?.scheduleDailyBird()
                }
                appGraph.notificationScheduler?.scheduleWeeklyRecap()
                appGraph.notificationScheduler?.scheduleTrophyProgress()
                appGraph.notificationScheduler?.cancelStreakRiskCheck()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != "birdy") return
        deepLinkFlow.tryEmit(uri.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        se.birdy.app.AndroidAppGraphHolder
            .clear()
        // Cancel in-flight init coroutine and free TFLite native handle.
        // Rotation destroys+recreates Activity so the classifier re-loads
        // (~14ms p95); singleton-lift to Application scope is a follow-up.
        appGraph.classifierBootstrap.close()
        billingClient.dispose()
    }

    @Suppress("LongMethod")
    private fun buildAppGraph(): AppGraph {
        val birdyData = BirdyData(DatabaseFactory(applicationContext).createDriver())
        val observationRepo = SqlDelightObservationRepository(birdyData.observationQueries)
        val badgeRepo = BadgeRepositoryImpl(birdyData.badgeUnlockQueries)
        // Catalog is small (25 badges from YAML); runBlocking ~10ms during onCreate is acceptable.
        val badgeCatalog = runBlocking { BadgeCatalogLoader.loadFromResources() }
        val badgeVersionStore = SharedPrefsBadgeVersionStore(applicationContext)
        val userPreferences = UserPreferencesStore(applicationContext).preferences()
        // One-shot migration: record firstInstallTimestamp if not yet set.
        // Existing v0.8.0-rc1 users (hasSeenOnboarding=true) get backdated to now-8d
        // so the 7d grace has already elapsed; the 3d throttle governs the next show.
        // Fresh installs get now → full 7d grace before any modal can appear.
        runBlocking {
            val existingInstall = userPreferences.firstInstallTimestamp.first()
            if (existingInstall == null) {
                val isUpgrade = userPreferences.hasSeenOnboarding.first()
                val installMs =
                    if (isUpgrade) {
                        System.currentTimeMillis() - UPGRADE_INSTALL_BACKDATE_MS
                    } else {
                        System.currentTimeMillis()
                    }
                userPreferences.setFirstInstallTimestamp(installMs)
            }
        }
        billingClient =
            se.birdy.app.data.premium.PremiumBillingClient(
                context = applicationContext,
                licensePublicKeyBase64 = BuildConfig.PLAY_LICENSE_KEY,
            )
        val premiumRepository =
            se.birdy.app.data.premium.BillingPremiumRepository(
                state = billingClient.state,
                queryPurchases = { billingClient.queryPurchases() },
            )
        // Connect + cold-start query in parallel with classifier bootstrap
        lifecycleScope.launch {
            billingClient.connect()
            billingClient.queryPurchases()
        }
        val classifierBootstrap = ClassifierBootstrap(buildClassifier = { buildClassifier() })
        // Plan 6b3 T7: build the PDF export use case. PdfFontProvider must be
        // initialised before the first render() call; doing it here keeps the
        // 57 KB typeface load out of the first export's critical path.
        PdfFontProvider.init(applicationContext)
        val journalRenderer = JournalPdfRenderer()
        // DEBUG-only Billing-verify escape hatch (runbook §1): when the developer
        // toggle is on, skip EVERY override so the real NotActive→purchase→Active
        // path is exercised even while PREMIUM_OPEN_FOR_LAUNCH=true. Read once here;
        // restart applies it. Always false in release (BuildConfig.DEBUG guards it).
        val skipPremiumOverride =
            BuildConfig.DEBUG && runBlocking { userPreferences.skipPremiumOverride.first() }
        // PREMIUM_OPEN_FOR_LAUNCH (defaultConfig=true) forces every user to Active(LIFETIME)
        // through the closed-testing + initial production window. Toggle off in
        // androidApp/build.gradle.kts when Billing v8 monetization goes live.
        val premiumOverride: PremiumState? =
            when {
                skipPremiumOverride -> null
                BuildConfig.PREMIUM_OPEN_FOR_LAUNCH ->
                    PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now())
                BuildConfig.DEBUG && BuildConfig.PREMIUM_DEBUG_FORCE_ACTIVE ->
                    PremiumState.Active(PremiumTier.YEARLY, Clock.System.now())
                else -> null
            }
        val overrideTag = runBlocking { userPreferences.appLanguage.first() }.toLocaleTagOrNull()
        val resolvedLocale =
            LocaleResolver.resolve(
                override = overrideTag,
                systemTag = resources.configuration.locales[0].toLanguageTag(),
            )
        val exportJournalUseCase =
            ExportJournalUseCase(
                observationRepo = observationRepo,
                speciesRepo = SpeciesRepositoryProvider.get(),
                badgeRepo = badgeRepo,
                catalog = badgeCatalog,
                render = { input, path -> journalRenderer.render(input, path) },
                userPreferences = userPreferences,
                outputPathFactory = { ms ->
                    val dir = File(cacheDir, "journal_exports").apply { mkdirs() }
                    File(dir, "birdy_field_journal_$ms.pdf").absolutePath
                },
                clock = Clock.System,
                timeZone = kotlinx.datetime.TimeZone.currentSystemDefault(),
                locale = resolvedLocale,
                // BadgeStringMap throws for badges it doesn't yet know (premium_field_member
                // strings land in Plan 6b3 T15/T16). Until then, fall back to a humanised ID.
                badgeNameResolver = { id -> resolveBadgeString(id) { BadgeStringMap.nameFor(id) } },
                badgeDescriptionResolver = { id -> resolveBadgeString(id) { BadgeStringMap.descriptionFor(id) } },
            )
        val dailyBirdHistory =
            se.birdy.data.dailybird
                .DailyBirdHistoryRepositoryImpl(birdyData)
        val dailyBirdSelector =
            se.birdy.domain.dailybird.DailyBirdSelector(
                speciesProvider = { SpeciesRepositoryProvider.get().allByQid(resolvedLocale) },
            )
        val notificationScheduler =
            se.birdy.app.notifications
                .NotificationSchedulerImpl(applicationContext)
        val platformNotificationsApi =
            se.birdy.app.notifications
                .AndroidPlatformNotificationsApi(applicationContext)
        return AppGraph(
            repository = SpeciesRepositoryProvider.get(),
            classifierBootstrap = classifierBootstrap,
            cameraSourceFactory = { AndroidCameraSource(applicationContext, this@MainActivity) },
            locationProvider =
                se.birdy.app.location
                    .AndroidLocationProvider(applicationContext),
            requestLocationPermission = {
                requestLocationPermLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            },
            observationRepository = observationRepo,
            photoStorage = PhotoStorageProvider.get(),
            badgeRepository = badgeRepo,
            badgeCatalog = badgeCatalog,
            badgeVersionStore = badgeVersionStore,
            userPreferences = userPreferences,
            premiumRepository = premiumRepository,
            premiumOverride = premiumOverride,
            versionName = BuildConfig.VERSION_NAME,
            defaultLocale = resolvedLocale,
            benchmarkScreen = buildBenchmarkScreen(classifierBootstrap),
            diagnosticsScreen = buildDiagnosticsScreen(classifierBootstrap, userPreferences),
            matchOverrideReader = buildMatchOverrideReader(),
            requestInAppReview = { launchInAppReview() },
            launchPurchase = { tier ->
                billingClient.launchPurchase(this@MainActivity, tier)
                Unit
            },
            formattedPricesFlow = billingClient.formattedPrices,
            audioClassifierProvider = audioProvider,
            audioStorageDir = {
                val dir = File(filesDir, "audio")
                dir.mkdirs()
                dir.absolutePath
            },
            audioRecorderFactory = { AndroidAudioRecorderAdapter() },
            waveformRendererFactory = { AndroidWaveformRenderer() },
            journalExport = { exportJournalUseCase.run() },
            selectDailyBird = { date -> dailyBirdSelector.selectFor(date) },
            notificationScheduler = notificationScheduler,
            dailyBirdHistory = dailyBirdHistory,
            platformNotificationsApi = platformNotificationsApi,
            requestPostNotificationsPermission = { requestPostNotificationsPermission() },
            devTriggerDailyBird =
                if (BuildConfig.DEBUG) {
                    {
                        androidx.work.WorkManager.getInstance(applicationContext).enqueue(
                            androidx.work
                                .OneTimeWorkRequestBuilder<
                                    se.birdy.app.notifications.workers.DailyBirdWorker,
                                >()
                                .build(),
                        )
                    }
                } else {
                    null
                },
            devTriggerWeeklyRecap =
                if (BuildConfig.DEBUG) {
                    {
                        androidx.work.WorkManager.getInstance(applicationContext).enqueue(
                            androidx.work
                                .OneTimeWorkRequestBuilder<
                                    se.birdy.app.notifications.workers.WeeklyRecapWorker,
                                >()
                                .setInputData(
                                    androidx.work.workDataOf(
                                        se.birdy.app.notifications.workers.WeeklyRecapWorker.KEY_FORCE_FOR_DEV to true,
                                    ),
                                ).build(),
                        )
                    }
                } else {
                    null
                },
            devTriggerTrophyProgress =
                if (BuildConfig.DEBUG) {
                    {
                        androidx.work.WorkManager.getInstance(applicationContext).enqueue(
                            androidx.work
                                .OneTimeWorkRequestBuilder<
                                    TrophyProgressWorker,
                                >()
                                .setInputData(
                                    androidx.work.workDataOf(
                                        TrophyProgressWorker.KEY_FORCE_FOR_DEV to true,
                                    ),
                                ).build(),
                        )
                    }
                } else {
                    null
                },
            deepLinkFlow = deepLinkFlow,
        )
    }

    private suspend fun resolveBadgeString(
        badgeId: String,
        resourceFor: () -> org.jetbrains.compose.resources.StringResource,
    ): String =
        runCatching {
            org.jetbrains.compose.resources
                .getString(resourceFor())
        }.getOrElse { humanizeBadgeId(badgeId) }

    private fun humanizeBadgeId(badgeId: String): String =
        badgeId
            .removePrefix("premium_")
            .split('_')
            .joinToString(" ") { part ->
                part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
            }

    private fun buildBenchmarkScreen(bootstrap: ClassifierBootstrap): (@Composable () -> Unit)? =
        if (BuildConfig.DEBUG) {
            @Composable {
                val ready = bootstrap.state.collectAsState().value as? ClassifierBootstrapState.Ready
                val version = ready?.modelVersion
                if (ready != null && version != null) {
                    se.birdy.app.debug.BenchmarkScreen(
                        classifier = ready.classifier,
                        modelVersion = version,
                    )
                }
            }
        } else {
            null
        }

    /**
     * Launches the Google Play in-app review prompt. No-op unless installed from Play
     * (debug/sideload silently does nothing); rate-limited by Play. Best-effort, fire-and-forget.
     */
    private fun launchInAppReview() {
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(this, task.result)
            }
        }
    }

    private fun buildDiagnosticsScreen(
        bootstrap: ClassifierBootstrap,
        userPreferences: UserPreferences,
    ): (@Composable () -> Unit)? =
        if (BuildConfig.DEBUG) {
            @Composable {
                val ready = bootstrap.state.collectAsState().value as? ClassifierBootstrapState.Ready
                // Keep the Billing-verify toggle reachable even before the classifier
                // is ready (billing testing must not depend on ML bootstrap state).
                val runner =
                    ready?.let { r ->
                        androidx.compose.runtime.remember(r.classifier) {
                            DiagnosticsRunner(context = applicationContext, classifier = r.classifier)
                        }
                    }
                DiagnosticsScreen(
                    runDiagnostic = {
                        runner?.run() ?: "Classifier not ready yet — try again in a moment."
                    },
                    skipPremiumOverride = userPreferences.skipPremiumOverride,
                    onSetSkipPremiumOverride = { userPreferences.setSkipPremiumOverride(it) },
                )
            }
        } else {
            null
        }

    private fun buildMatchOverrideReader(): (() -> se.birdy.app.ui.match.MatchOverride?)? =
        if (BuildConfig.DEBUG) {
            {
                val f = File(filesDir, "match_override.txt")
                if (f.exists()) {
                    runCatching {
                        se.birdy.app.ui.match
                            .parseMatchOverride(f.readText())
                    }.getOrNull()
                } else {
                    null
                }
            }
        } else {
            null
        }

    private suspend fun buildClassifier(): Triple<BirdClassifier, ClassifierMode, String?> {
        val artifactProvider = ModelArtifactProvider()
        var capturedModelVersion: String? = null
        val factory =
            BirdClassifierFactory(
                createReal = {
                    val info = loadModelMetadata()
                    capturedModelVersion = info.modelVersion
                    val mapper = loadAiyLabelMapper()
                    val modelBytes = artifactProvider.loadModelBytes(info)
                    val runner = AndroidTfliteRunner(modelBytes, info)
                    val preprocessor = ImagePreprocessor()
                    TfLiteBirdClassifier(
                        info = info,
                        runner = runner,
                        preprocess = { input, modelInfo ->
                            preprocessor.preprocess(
                                input = input,
                                outHeight = modelInfo.inputHeightPx,
                                outWidth = modelInfo.inputWidthPx,
                                normalizationMean = modelInfo.normalizationMean.toFloatArray(),
                                normalizationStd = modelInfo.normalizationStd.toFloatArray(),
                            )
                        },
                        mapper = mapper,
                    )
                },
                // MUST be cheap + non-throwing — BirdClassifierFactory does not guard the DEMO-path fallback.
                createFallback = { FakeBirdClassifier() },
                onCrashlytics = { t ->
                    android.util.Log.e("Birdy", "TFLite init failed, falling back to Fake", t)
                    // FirebaseCrashlytics integration deferred — Plan 6 polish.
                },
            )
        val (classifier, mode) = factory.create()
        // capturedModelVersion is null when createReal threw and we fell back to DEMO.
        return Triple(classifier, mode, capturedModelVersion)
    }

    private fun cleanOldCacheFrames() {
        val cutoff = System.currentTimeMillis() - ONE_HOUR_MS
        listOf("scan-frames", "photo-input").forEach { sub ->
            val dir = File(cacheDir, sub)
            if (!dir.exists()) return@forEach
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }
    }

    private companion object {
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
    }
}
