package se.birdy.app

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSLog
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.preferredLanguages
import se.birdy.app.badges.BadgeCatalogLoader
import se.birdy.app.bootstrap.BadgeVersionStore
import se.birdy.app.di.AppGraph
import se.birdy.app.i18n.LocaleResolver
import se.birdy.app.i18n.toLocaleTagOrNull
import se.birdy.app.location.IosLocationPermissionRequester
import se.birdy.app.location.IosLocationProvider
import se.birdy.app.notifications.IosNotificationPermission
import se.birdy.app.notifications.IosNotificationScheduler
import se.birdy.app.notifications.IosPlatformNotificationsApi
import se.birdy.app.notifications.devNotifTrigger
import se.birdy.app.notifications.todayLocalDate
import se.birdy.app.photo.PhotoStorageProvider
import se.birdy.app.ui.audio.IosAudioRecorderAdapter
import se.birdy.app.ui.audio.IosWaveformRenderer
import se.birdy.app.util.ioDispatcher
import se.birdy.data.DatabaseFactory
import se.birdy.data.badge.BadgeRepositoryImpl
import se.birdy.data.db.BirdyData
import se.birdy.data.observation.SqlDelightObservationRepository
import se.birdy.datastore.UserPreferencesStore
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import se.birdy.ml.AudioClassifierFactory
import se.birdy.ml.AudioClassifierMode
import se.birdy.ml.BirdAudioClassifier
import se.birdy.ml.BirdClassifierFactory
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.FakeAudioClassifier
import se.birdy.ml.FakeBirdClassifier
import se.birdy.ml.ImagePreprocessor
import se.birdy.ml.IosTfliteAudioRunner
import se.birdy.ml.IosTfliteRunner
import se.birdy.ml.ModelArtifactProvider
import se.birdy.ml.TfLiteBirdClassifier
import se.birdy.ml.camera.IosCameraSource
import se.birdy.ml.loadAiyLabelMapper
import se.birdy.ml.loadModelMetadata
import kotlin.concurrent.AtomicReference
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * iOS composition root — the iOS counterpart of MainActivity.buildAppGraph().
 *
 * Remaining stubs (each lifted by its owning plan):
 * - premiumOverride Active(LIFETIME): launch-parity with Android's
 *   PREMIUM_OPEN_FOR_LAUNCH; real StoreKit gating lands in i5.
 *
 * i1 resolved: UserPreferences + BadgeVersionStore now persist (NSUserDefaults).
 * i2b resolved: buildClassifier() mirrors Android's real TFLite classifier wiring.
 * i2c resolved: live-camera scan is now REAL (IosCameraSource, AVFoundation) — only the
 *   premium override remains.
 * i3 resolved: audio-ID wirad (IosTfliteAudioRunner + IosAudioRecorder; Flex endast device — sim visar felstate/DEMO).
 * i4 T6 resolved: locationProvider/requestLocationPermission wirade (IosLocationProvider,
 *   CoreLocation one-shot + IosLocationPermissionRequester) — kartans opt-in fynd-platsfångst
 *   fungerar nu på iOS, spegel av MainActivity.buildAppGraph().
 * i4 T9 resolved: platformNotificationsApi/requestPostNotificationsPermission wirade
 *   (IosPlatformNotificationsApi — UNUserNotificationCenter-cachad auth-status;
 *   IosNotificationPermission — permission-flödet) + AppGraphHolderIos (spegel av
 *   AndroidAppGraphHolder, löser cirkulariteten mellan grafbygget och permission-callbacken).
 *   Stänger även en upptäckt paritetslucka: selectDailyBird/dailyBirdHistory hade ALDRIG
 *   wire:ats på iOS, så "Dagens fågel" har varit dött på Lyssna-fliken.
 * i4 T10 resolved: notificationScheduler wirad (IosNotificationScheduler —
 *   UNCalendarNotificationTrigger, innehåll räknas färskt per schedule*-anrop) +
 *   devTriggerDailyBird/WeeklyRecap/TrophyProgress (Platform.isDebugBinary-gated, null i
 *   release — speglar Android BuildConfig.DEBUG). Notis-delegaten installeras INTE härifrån:
 *   den måste finnas innan appen är klar med att starta (Apples kallstart-tap-krav), så
 *   `iOSApp.swift`s init() anropar installIosNotificationDelegate() tidigt, långt innan den
 *   här grafen byggs; MainViewController.kt anropar installIosNotificationLifecycle(graph)
 *   efteråt för foreground-omschemaläggning + för att dränera en ev. kallstart-stashad
 *   deep-link (se IosNotificationLifecycle.kt:s KDoc för hela resonemanget).
 */
fun buildIosAppGraph(): AppGraph {
    val birdyData = BirdyData(DatabaseFactory().createDriver())
    val observationRepo = SqlDelightObservationRepository(birdyData.observationQueries)
    val badgeRepo = BadgeRepositoryImpl(birdyData.badgeUnlockQueries)
    val badgeCatalog = runBlocking { BadgeCatalogLoader.loadFromResources() }
    val classifierBootstrap =
        ClassifierBootstrap(
            buildClassifier = {
                val artifactProvider = ModelArtifactProvider()
                var capturedModelVersion: String? = null
                val factory =
                    BirdClassifierFactory(
                        createReal = {
                            val info = loadModelMetadata()
                            capturedModelVersion = info.modelVersion
                            val mapper = loadAiyLabelMapper()
                            val modelBytes = artifactProvider.loadModelBytes(info)
                            val runner = IosTfliteRunner(modelBytes, info)
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
                        onCrashlytics = { /* no Crashlytics on iOS yet — swallow; factory falls back to FakeBirdClassifier + DEMO */ },
                    )
                val (classifier, mode) = factory.create()
                // capturedModelVersion is null when createReal threw and we fell back to DEMO.
                Triple(classifier, mode, capturedModelVersion)
            },
        )
    val userPreferences = UserPreferencesStore(null).preferences()
    // Mirror of MainActivity.buildAppGraph()'s locale resolution (line 323-328).
    // Without this, species content is hard-locked to the AppGraph default Locale.SV
    // regardless of device language and the app_language pref. A fresh graph is built
    // per app launch, so language changes take effect on next launch (live-switch = i4).
    val storedLanguage = runBlocking { userPreferences.appLanguage.first() }
    val resolvedLocale =
        LocaleResolver.resolve(
            override = storedLanguage.toLocaleTagOrNull(),
            systemTag = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en",
        )
    val dailyBirdHistory =
        se.birdy.data.dailybird
            .DailyBirdHistoryRepositoryImpl(birdyData)
    val dailyBirdSelector =
        se.birdy.domain.dailybird.DailyBirdSelector(
            speciesProvider = { SpeciesRepositoryProvider.get().allByQid(resolvedLocale) },
        )
    val platformNotificationsApi = IosPlatformNotificationsApi()
    val deepLinkFlow =
        kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
    return AppGraph(
        repository = SpeciesRepositoryProvider.get(),
        classifierBootstrap = classifierBootstrap,
        cameraSourceFactory = { IosCameraSource() },
        locationProvider = IosLocationProvider(),
        requestLocationPermission = { IosLocationPermissionRequester.request() },
        observationRepository = observationRepo,
        photoStorage = PhotoStorageProvider.get(),
        badgeRepository = badgeRepo,
        badgeCatalog = badgeCatalog,
        badgeVersionStore = NsUserDefaultsBadgeVersionStore(),
        userPreferences = userPreferences,
        premiumRepository = IosStubPremiumRepository(),
        premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()),
        audioClassifierProvider = IosAudioBootstrap.provider,
        audioStorageDir = ::audioStorageDirPath,
        audioRecorderFactory = { IosAudioRecorderAdapter() },
        waveformRendererFactory = { IosWaveformRenderer() },
        versionName = "1.2.0-ios-i3",
        defaultLocale = resolvedLocale,
        selectDailyBird = { date -> dailyBirdSelector.selectFor(date) },
        dailyBirdHistory = dailyBirdHistory,
        platformNotificationsApi = platformNotificationsApi,
        requestPostNotificationsPermission = { IosNotificationPermission.request(graphAccessor = { AppGraphHolderIos.current }) },
        notificationScheduler = IosNotificationScheduler(graphAccessor = { AppGraphHolderIos.current }),
        devTriggerDailyBird = devNotifTrigger { payloads, _ -> payloads.dailyBird(todayLocalDate()) },
        devTriggerWeeklyRecap = devNotifTrigger { payloads, _ -> payloads.weeklyRecap(forceForDev = true) },
        devTriggerTrophyProgress = devNotifTrigger { payloads, _ -> payloads.trophyProgress(forceForDev = true) },
        deepLinkFlow = deepLinkFlow,
    )
}

/**
 * Process-global handle så iOS-permission-callbacken (som fyras från en godtycklig
 * completion-handler-kö, INTE komposition) kan nå den byggda [AppGraph] — spegel av
 * Androids `AndroidAppGraphHolder`. Löser cirkulariteten: `requestPostNotificationsPermission`-
 * lambdan måste fångas INNAN [AppGraph] existerar, så den fångar en accessor-lambda mot
 * den här holdern istället för grafen direkt. Sätts i `MainViewController.kt` direkt efter
 * `buildIosAppGraph()`; grafen dör aldrig under appens livstid (ingen clear-motsvarighet
 * till Androids onDestroy behövs).
 */
internal object AppGraphHolderIos {
    var current: AppGraph? = null
}

/**
 * iOS-spegel av MainActivitys audio-bootstrap (Deferred-CAS-cache): modellen (54 MB)
 * laddas högst en gång, LAZY så förlorar-grenens Deferred aldrig startar, och en
 * FAILAD Deferred evictas före rethrow så "Försök igen" gör ett RIKTIGT nytt försök
 * (vC127-fix #8). Skillnader mot Android: scope är app-livstid (grafen dör aldrig på
 * iOS — ingen onDestroy-close behövs), BuildConfig.DEBUG → Platform.isDebugBinary.
 */
internal object IosAudioBootstrap {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache =
        AtomicReference<Deferred<Pair<BirdAudioClassifier, AudioClassifierMode>>?>(null)

    @Suppress("TooGenericExceptionCaught")
    val provider: suspend () -> Pair<BirdAudioClassifier, AudioClassifierMode> =
        provider@{
            while (true) {
                val cached = cache.value
                val deferred: Deferred<Pair<BirdAudioClassifier, AudioClassifierMode>> =
                    if (cached != null) {
                        cached
                    } else {
                        val newDeferred =
                            // ioDispatcher = Dispatchers.Default on iOS (Dispatchers.IO is
                            // internal on Kotlin/Native in kotlinx-coroutines 1.9.0) — reuse
                            // the shared expect/actual instead of a local comment; see
                            // composeApp/src/*/kotlin/se/birdy/app/util/IoDispatcher.kt.
                            scope.async(ioDispatcher, start = CoroutineStart.LAZY) { build() }
                        if (cache.compareAndSet(null, newDeferred)) {
                            newDeferred
                        } else {
                            cache.value ?: continue
                        }
                    }
                val result =
                    try {
                        deferred.await()
                    } catch (t: Throwable) {
                        // Generisk catch avsiktlig (spegel av MainActivity): native-laddfel
                        // kan vara Errors. Evicta ENDAST när deferred SJÄLV failade — en
                        // caller-cancel får inte evicta en frisk in-flight-load.
                        @OptIn(ExperimentalCoroutinesApi::class)
                        val deferredFailed =
                            deferred.isCompleted && deferred.getCompletionExceptionOrNull() != null
                        if (deferredFailed) {
                            cache.compareAndSet(deferred, null)
                        }
                        throw t
                    }
                if (cache.value === deferred) {
                    return@provider result
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("audioProvider loop exited unexpectedly")
        }

    @OptIn(ExperimentalNativeApi::class)
    private suspend fun build(): Pair<BirdAudioClassifier, AudioClassifierMode> =
        AudioClassifierFactory(
            createReal = { IosTfliteAudioRunner.load(bundledBirdnetPath()) },
            createFallback = { FakeAudioClassifier() },
            onDegrade = { t ->
                // Enda arg-formen: samma K/N-vararg-marshaling-fälla som MapTilerKey.ios.kt
                // dokumenterar (NSLog(format: String, vararg Any?) kraschar om en rå Kotlin
                // String skickas som vararg-elementet). Nytt här: meddelandet är fri text
                // (t.message) som kan innehålla "%" — %%-escapas innan det blir
                // `format`-argumentet, annars tolkar CoreFoundation tecknen som formatdirektiv.
                NSLog("Birdy/audio: classifier degrade: ${t::class.simpleName}: ${t.message}".replace("%", "%%"))
            },
            allowFallback = Platform.isDebugBinary,
        ).create()

    private fun bundledBirdnetPath(): String =
        NSBundle.mainBundle.pathForResource("birdnet_lite_v2", ofType = "tflite")
            ?: error("birdnet_lite_v2.tflite saknas i app-bundlen — kontrollera project.yml-resursen (i3 T1)")
}

@OptIn(ExperimentalForeignApi::class)
internal fun audioStorageDirPath(): String {
    val docs =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .first() as String
    val dir = "$docs/audio"
    NSFileManager.defaultManager.createDirectoryAtPath(
        dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return dir
}

/** Persists the last badge-catalog version we backfilled, so it does not re-run each launch. */
internal class NsUserDefaultsBadgeVersionStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : BadgeVersionStore {
    override var lastSeen: Int
        get() = defaults.integerForKey(KEY).toInt()
        set(value) {
            defaults.setInteger(value.toLong(), forKey = KEY)
        }

    private companion object {
        const val KEY = "birdy_badges.catalog_version_last_seen"
    }
}

/** Local premium state only; real StoreKit repository lands in plan i5. */
internal class IosStubPremiumRepository : PremiumRepository {
    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    override val state: StateFlow<PremiumState> = _state.asStateFlow()

    override suspend fun markPurchased(tier: PremiumTier) {
        _state.value = PremiumState.Active(tier, Clock.System.now())
    }

    override suspend fun restore() = Unit
}
