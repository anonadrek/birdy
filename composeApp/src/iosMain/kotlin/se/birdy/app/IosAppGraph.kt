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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
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
import se.birdy.app.notifications.NotificationPayloads
import se.birdy.app.notifications.devNotifTrigger
import se.birdy.app.notifications.todayLocalDate
import se.birdy.app.photo.PhotoStorageProvider
import se.birdy.app.ui.audio.IosAudioRecorderAdapter
import se.birdy.app.ui.audio.IosWaveformRenderer
import se.birdy.app.ui.badges.BadgeStringMap
import se.birdy.app.ui.badges.resolveBadgeString
import se.birdy.app.usecase.ExportJournalUseCase
import se.birdy.app.util.ioDispatcher
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
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
import se.birdy.pdf.JournalPdfRenderer
import kotlin.concurrent.AtomicReference
import kotlin.concurrent.Volatile
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
 *   Review-fix: [IosSpeciesByQidMemoHolder] + [iosNotificationPayloads] memoiserar
 *   artkartan (839 arter) process-livstid för notis-payloads + Dagens fågel — se
 *   [IosSpeciesByQidMemoHolder]s KDoc för varför.
 * i4 T12 resolved: journalExport wirad (riktig [se.birdy.pdf.JournalPdfRenderer] via
 *   UIGraphicsPDFRenderer + [se.birdy.app.usecase.ExportJournalUseCase]) — spegel av
 *   MainActivity.kt:s exportJournalUseCase-bygge; badge-strängarna löses via delade
 *   [resolveBadgeString] (ui/badges/BadgeStringResolver.kt). Arkiv-fliken visar nu
 *   PDF-export-CTA:n på iOS också.
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
    // i4 T12: PDF-export use case, spegel av MainActivity.kt:375-394 (samma use case,
    // iOS-paths). journalRenderer/exportJournalUseCase byggs precis som på Android direkt
    // efter resolvedLocale är känd, eftersom use caset behöver den för species-lookup.
    val journalRenderer = JournalPdfRenderer()
    val exportJournalUseCase =
        ExportJournalUseCase(
            observationRepo = observationRepo,
            speciesRepo = SpeciesRepositoryProvider.get(),
            badgeRepo = badgeRepo,
            catalog = badgeCatalog,
            render = { input, path -> journalRenderer.render(input, path) },
            userPreferences = userPreferences,
            outputPathFactory = { ms -> "${journalExportDirPath()}/birdy_field_journal_$ms.pdf" },
            clock = Clock.System,
            timeZone = kotlinx.datetime.TimeZone.currentSystemDefault(),
            locale = resolvedLocale,
            // BadgeStringMap kastar för badge-id:n den inte känner igen — delade
            // resolveBadgeString faller tillbaka på en humaniserad id-sträng.
            badgeNameResolver = { id -> resolveBadgeString(id) { BadgeStringMap.nameFor(id) } },
            badgeDescriptionResolver = { id -> resolveBadgeString(id) { BadgeStringMap.descriptionFor(id) } },
        )
    val dailyBirdHistory =
        se.birdy.data.dailybird
            .DailyBirdHistoryRepositoryImpl(birdyData)
    // Review-fix (i4 T10): ONE memo for the process, not one per schedule*() pass — see
    // IosSpeciesByQidMemoHolder's KDoc. Both consumers (Dagens fågel selector below +
    // iosNotificationPayloads, wired into the graph further down) route through it.
    val speciesByQidMemo = SuspendMemo { SpeciesRepositoryProvider.get().allByQid(resolvedLocale) }
    IosSpeciesByQidMemoHolder.memo = speciesByQidMemo
    val dailyBirdSelector =
        se.birdy.domain.dailybird.DailyBirdSelector(
            speciesProvider = { speciesByQidMemo.get() },
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
        journalExport = { exportJournalUseCase.run() },
        versionName = "1.2.0-ios-i4",
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
 *
 * Åtkomstkontrakt för [current]: skrivs EN gång, på huvudtråden, vid första compositionen.
 * Läses därefter både från huvudtrådscoroutines och från godtyckliga
 * `UNUserNotificationCenter`-callback-köer (Task 10:s `BirdyNotificationDelegate`) —
 * därför @Volatile, så skrivningen garanterat syns över tråd-gränsen.
 */
internal object AppGraphHolderIos {
    @Volatile
    var current: AppGraph? = null
}

/**
 * Process-global handle till artkarte-memot (qid→Species, hela 839-artstabellen) —
 * spegel av [AppGraphHolderIos]. Satt EN gång i [buildIosAppGraph]; läst av
 * [iosNotificationPayloads], som anropas från en ANNAN fil/paket
 * (`se.birdy.app.notifications.IosNotificationScheduler`/`IosNotificationLifecycle`) och
 * annars inte skulle nå den memo-instans grafbygget skapade.
 *
 * **Varför ett memo överhuvudtaget:** artdatabasen är statiskt bundlad innehåll och
 * [AppGraph.defaultLocale] är fixerad för hela processens livstid (exakt EN [AppGraph]
 * byggs någonsin — se [AppGraphHolderIos]s KDoc) → ett per-process-cache har NOLL
 * staleness-risk. Utan det materialiserade VARJE `schedule*()`-anrop (T10-reviewfynd)
 * hela artkartan (sex SELECT-ALL-frågor + 839 `Species`-objekt) på nytt: 7× för
 * dagens-fågel-fönstret + 1× för `trophyProgress()` = 8× per omschemaläggnings-pass,
 * körd vid VARJE `UIApplicationDidBecomeActive` (kallstart ≈ två pass ≈ 16 laddningar) —
 * och `pushPermissionAsked`-grinden gäller bara "har frågats", så även användare som
 * NEKAT push-behörighet betalar kostnaden. Memot gör detta 1× per process istället.
 */
internal object IosSpeciesByQidMemoHolder {
    internal var memo: SuspendMemo<Map<SpeciesId, Species>>? = null
}

/**
 * iOS-spegel av [NotificationPayloads.Companion.from] — MÅSTE hållas synkad med den
 * fält-för-fält (samma lambdor, samma defaults) om commonMains `from(graph)` någonsin
 * ändras. `NotificationPayloads`/`DailyBirdSelector` i commonMain rörs INTE av detta —
 * konstruktorn är public precis för att tillåta den här typen av alternativ wiring på en
 * enskild plattform. Enda skillnaden mot `from(graph)`: `speciesByQid` går via
 * [IosSpeciesByQidMemoHolder] istället för att materialisera artkartan på nytt (fallbacken
 * till en ny, omemoiserad `allByQid`-läsning är en ren defensiv gard — memot är i praktiken
 * ALLTID satt här, eftersom [buildIosAppGraph] sätter det innan grafen ens returneras och
 * ingen konsument kan nå denna funktion utan en redan byggd graf).
 */
internal fun iosNotificationPayloads(graph: AppGraph): NotificationPayloads =
    NotificationPayloads(
        prefs = graph.userPreferences,
        observationRepo = graph.observationRepository,
        badgeRepo = graph.badgeRepository,
        badgeCatalog = graph.badgeCatalog,
        speciesByQid = {
            // Fallback matchar from()'s omemoiserade uttryck exakt (defensiv gard — se KDoc ovan).
            IosSpeciesByQidMemoHolder.memo?.get() ?: graph.repository.allByQid(graph.defaultLocale)
        },
        speciesNameFor = { qid ->
            graph.repository
                .getById(SpeciesId(qid), graph.defaultLocale)
                .first()
                ?.name
        },
        selectDailyBird = graph.selectDailyBird,
        dailyBirdMatchCount = { graph.dailyBirdHistory?.totalMatchCount() ?: 0 },
        timeZone = graph.timeZone,
        clock = graph.clock,
    )

/**
 * Process-lifetime "compute once, cache forever" för dyra suspend-laddningar med en
 * fast/static källa (t.ex. den bundlade artdatabasen). Mutex:en förhindrar en dubbel-
 * beräkning om två callers råkar racea in innan [value] hunnit sättas — den yttre
 * null-checken är en billig fast path som slår till för alla anrop EFTER den första.
 * [value] är @Volatile: den olåsta fast path-läsningen sker UTAN mutex:en och dess
 * anropande coroutine-context är inte garanterat huvudtråden (flera olika callers delar
 * detta memo) — utan @Volatile finns ingen minnesmodell-garanti att en annan tråds
 * skrivning syns.
 */
internal class SuspendMemo<T : Any>(
    private val compute: suspend () -> T,
) {
    private val mutex = Mutex()

    @Volatile
    private var value: T? = null

    suspend fun get(): T = value ?: mutex.withLock { value ?: compute().also { value = it } }
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

/**
 * Absolute path to `<Caches>/journal_exports`, creating it if missing — the iOS counterpart
 * of MainActivity's `File(cacheDir, "journal_exports")` (rad 383-386). Caches, not Documents
 * (unlike [audioStorageDirPath]): exported PDFs are share-sheet ephemera, not user data that
 * needs to survive an iCloud/Finder backup restore, matching Android's cacheDir choice.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun journalExportDirPath(): String {
    val caches =
        NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .first() as String
    val dir = "$caches/journal_exports"
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
