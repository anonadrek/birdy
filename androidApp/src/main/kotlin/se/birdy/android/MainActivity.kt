package se.birdy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
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
import se.birdy.app.photo.PhotoStorageProvider
import se.birdy.app.ui.debug.DiagnosticsRunner
import se.birdy.app.ui.debug.DiagnosticsScreen
import se.birdy.app.ui.settings.AppLocaleApplier
import se.birdy.app.ui.settings.SettingsLauncherSetup
import se.birdy.data.DatabaseFactory
import se.birdy.data.badge.BadgeRepositoryImpl
import se.birdy.data.db.BirdyData
import se.birdy.data.observation.SqlDelightObservationRepository
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
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import android.graphics.Color as AndroidColor

class MainActivity : ComponentActivity() {
    private lateinit var appGraph: AppGraph
    private lateinit var billingClient: se.birdy.app.data.premium.PremiumBillingClient

    /**
     * Caches the in-flight or completed audio-classifier [Deferred] so the 57 MB TFLite
     * model is loaded at most once per session. [AtomicReference] + CAS ensures only one
     * coroutine wins the load race; losers use [CoroutineStart.LAZY] so their Deferred
     * body never runs and no native handle is created (Fix #2).
     *
     * Reset to null in [onTrimMemory] (level >= TRIM_MEMORY_BACKGROUND) to free the
     * native TFLite handle when the app moves to background.
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
     * Fix #5: Retry loop re-checks the cache reference after [Deferred.await] returns.
     * If [onTrimMemory] cleared the cache and closed the classifier while we were
     * suspended, we loop to build a fresh instance rather than returning a closed one.
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
                val result = deferred.await()
                // Re-check: if onTrimMemory cleared the cache while we awaited, the
                // classifier was closed. Loop to build a fresh one (Fix #5).
                if (audioBootstrapCache.get() === deferred) {
                    return@audioProvider result
                }
                // Cache was cleared — classifier is closed. Loop.
            }
            @Suppress("UNREACHABLE_CODE")
            error("audioProvider loop exited unexpectedly")
        }

    /**
     * Builds the audio classifier via [AudioClassifierFactory]. Extracted from [audioProvider]
     * to keep the lambda readable. On failure, falls back to a no-op classifier and logs a warning;
     * Crashlytics integration is deferred to Plan 6b3.
     */
    private suspend fun buildAudioClassifier(): Pair<BirdAudioClassifier, AudioClassifierMode> =
        AudioClassifierFactory(
            createReal = { AndroidTfliteAudioRunner.load(applicationContext) },
            createFallback = { FakeAudioClassifier() },
            onDegrade = { t -> android.util.Log.w("Birdy", "Audio TFLite init failed, falling back to Fake", t) },
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
        setContent { App(appGraph) }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel in-flight init coroutine and free TFLite native handle.
        // Rotation destroys+recreates Activity so the classifier re-loads
        // (~14ms p95); singleton-lift to Application scope is a follow-up.
        appGraph.classifierBootstrap.close()
        billingClient.dispose()
    }

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
                        System.currentTimeMillis() - 8L * 24 * 3600 * 1000
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
        val premiumOverride: PremiumState? =
            if (BuildConfig.PREMIUM_DEBUG_FORCE_ACTIVE) {
                PremiumState.Active(PremiumTier.YEARLY, Clock.System.now())
            } else {
                null
            }
        val overrideTag = runBlocking { userPreferences.appLanguage.first() }.toLocaleTagOrNull()
        val resolvedLocale =
            LocaleResolver.resolve(
                override = overrideTag,
                systemTag = resources.configuration.locales[0].toLanguageTag(),
            )
        return AppGraph(
            repository = SpeciesRepositoryProvider.get(),
            classifierBootstrap = classifierBootstrap,
            cameraSourceFactory = { AndroidCameraSource(applicationContext, this@MainActivity) },
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
            diagnosticsScreen = buildDiagnosticsScreen(classifierBootstrap),
            matchOverrideReader = buildMatchOverrideReader(),
            launchPurchase = { tier ->
                billingClient.launchPurchase(this@MainActivity, tier)
                Unit
            },
            formattedPricesFlow = billingClient.formattedPrices,
            audioClassifierProvider = audioProvider,
        )
    }

    /**
     * Releases the cached audio classifier when the system signals memory pressure at or
     * above [TRIM_MEMORY_BACKGROUND]. The AtomicReference is reset to null so the next
     * audio-scan entry re-loads the model fresh.
     *
     * [GlobalScope] + [NonCancellable] mirrors the pattern used for image-classifier
     * cleanup in [buildClassifier] (see Plan 4b): [lifecycleScope] may already be
     * cancelled at this point on some Android versions.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            val cached = audioBootstrapCache.getAndSet(null)
            cached?.let { def ->
                GlobalScope.launch(Dispatchers.IO + NonCancellable) {
                    runCatching { def.await().first.close() }
                }
            }
        }
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

    private fun buildDiagnosticsScreen(bootstrap: ClassifierBootstrap): (@Composable () -> Unit)? =
        if (BuildConfig.DEBUG) {
            @Composable {
                val ready = bootstrap.state.collectAsState().value as? ClassifierBootstrapState.Ready
                if (ready != null) {
                    val classifier = ready.classifier
                    val runner =
                        androidx.compose.runtime.remember(classifier) {
                            DiagnosticsRunner(context = applicationContext, classifier = classifier)
                        }
                    DiagnosticsScreen(runDiagnostic = { runner.run() })
                }
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
