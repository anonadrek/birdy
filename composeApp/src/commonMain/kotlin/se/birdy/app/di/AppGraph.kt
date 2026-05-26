package se.birdy.app.di

import androidx.compose.runtime.Composable
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.bootstrap.BadgeBackfillOnAppStart
import se.birdy.app.bootstrap.BadgeVersionStore
import se.birdy.app.data.premium.FormattedPrices
import se.birdy.app.photo.PhotoStorage
import se.birdy.app.ui.audio.AudioRecorderApi
import se.birdy.app.ui.audio.AudioScanViewModel
import se.birdy.app.ui.audio.WaveformRendererApi
import se.birdy.app.ui.badges.BadgesViewModel
import se.birdy.app.ui.diary.LifelistViewModel
import se.birdy.app.ui.diary.ObservationDetailViewModel
import se.birdy.app.ui.encyclopedia.ArchiveViewModel
import se.birdy.app.ui.listen.ListenLauncherViewModel
import se.birdy.app.ui.match.MatchOverride
import se.birdy.app.ui.match.MatchResultViewModel
import se.birdy.app.ui.onboarding.OnboardingViewModel
import se.birdy.app.ui.photoanalyze.PhotoAnalyzeViewModel
import se.birdy.app.ui.premium.PremiumViewModel
import se.birdy.app.ui.profile.SpeciesProfileViewModel
import se.birdy.app.ui.scan.ScanViewModel
import se.birdy.app.ui.settings.SettingsViewModel
import se.birdy.app.ui.stats.SeasonStatsViewModel
import se.birdy.app.usecase.JournalExportResult
import se.birdy.app.usecase.SaveObservationUseCase
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.datastore.UserPreferences
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.observation.ObservationRepository
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import se.birdy.ml.AudioClassifierMode
import se.birdy.ml.BirdAudioClassifier
import se.birdy.ml.BirdClassifier
import se.birdy.ml.CameraSource
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.ClassifierBootstrapState
import se.birdy.ml.ClassifierMode
import se.birdy.ml.ScanSourceSerialization
import se.birdy.ml.toScanSource

class AppGraph(
    val repository: SpeciesRepository,
    val classifierBootstrap: ClassifierBootstrap,
    val cameraSourceFactory: () -> CameraSource,
    val observationRepository: ObservationRepository,
    val photoStorage: PhotoStorage,
    val badgeRepository: BadgeRepository,
    val badgeCatalog: BadgeCatalog,
    val badgeVersionStore: BadgeVersionStore,
    val userPreferences: UserPreferences,
    val premiumRepository: PremiumRepository,
    val premiumOverride: PremiumState? = null,
    val versionName: String = "0.0.0",
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val defaultLocale: Locale = Locale.SV,
    /**
     * Non-null = show debug overflow menu + register debug route.
     * Null = DEBUG features hidden (release builds, DEMO mode).
     * The composable lambda is androidMain-only; defined in MainActivity.
     */
    val benchmarkScreen: (@Composable () -> Unit)? = null,
    /**
     * DEBUG-only ML preprocessing diagnostics screen (Plan 6b1 T2).
     * Non-null = adds "ML diagnos" entry to Archive overflow menu.
     * Null = release builds, screen + route not registered.
     */
    val diagnosticsScreen: (@Composable () -> Unit)? = null,
    /**
     * DEBUG-only hook for deterministic Match/Disambig/NoBird testing.
     * Reads files/match_override.txt; null on release builds.
     * See Plan 6b1 T3 + docs/superpowers/runbooks/2026-05-16-test-image-infra.md.
     */
    val matchOverrideReader: (() -> MatchOverride?)? = null,
    /**
     * Real Google Play Billing purchase launcher (Plan 6b1 T4).
     * Null = fall back to repository.markPurchased (legacy stub / tests).
     * Android actual: billingClient.launchPurchase(activity, tier).
     */
    val launchPurchase: (suspend (PremiumTier) -> Unit)? = null,
    /**
     * Live formatted prices from ProductDetails (Plan 6b1 T4).
     * Null = no live prices; PremiumUiState keeps null price fields.
     */
    val formattedPricesFlow: kotlinx.coroutines.flow.StateFlow<FormattedPrices>? = null,
    /**
     * Lazy audio classifier provider — only invoked on first audio-scan entry (Plan 6b2 T3).
     *
     * Returns a [Pair] of [BirdAudioClassifier] and [AudioClassifierMode] so the UI can
     * display a DEMO banner when the real BirdNET-Lite model failed to load (mirrors the
     * image-pipeline DEMO banner driven by [classifierMode]).
     *
     * The lambda is called by the audio pipeline when the user first requests audio-ID.
     * The Android actual caches the result via
     * `AtomicReference<Deferred<Pair<BirdAudioClassifier, AudioClassifierMode>>?>` + CAS
     * in [MainActivity] so concurrent callers get the same instance and the 57 MB TFLite
     * model is loaded at most once per session.
     *
     * Null in tests / non-Android targets — UI must gate audio-scan entry behind a
     * premium check AND a non-null provider check.
     */
    val audioClassifierProvider: (suspend () -> Pair<BirdAudioClassifier, AudioClassifierMode>)? = null,
    /**
     * Returns the absolute path to the directory where audio recordings are stored.
     * Must be callable from any thread; the caller ensures [mkdirs] is invoked before
     * returning. Wired from [MainActivity] as `{ File(filesDir, "audio").also { it.mkdirs() }.absolutePath }`.
     *
     * Null in tests — [audioScanViewModel] will error if called without this wired.
     */
    val audioStorageDir: (() -> String)? = null,
    /**
     * Factory for [AudioRecorderApi]. Android actual returns [AndroidAudioRecorderAdapter].
     * Null in tests / non-Android targets — [audioScanViewModel] will error if null.
     */
    val audioRecorderFactory: (() -> AudioRecorderApi)? = null,
    /**
     * Factory for [WaveformRendererApi]. T5 returns [AndroidWaveformRendererStub];
     * T6 upgrades to the real PNG + Opus renderer.
     * Null in tests / non-Android targets — [audioScanViewModel] will error if null.
     */
    val waveformRendererFactory: (() -> WaveformRendererApi)? = null,
    /**
     * Plan 6b3 T7: assembles the journal PDF on-demand and returns the result.
     *
     * Android actual delegates to [se.birdy.app.usecase.ExportJournalUseCase] wired in
     * [se.birdy.android.MainActivity]; the UI layer hands the resulting path to the
     * platform share-sheet via [se.birdy.app.ui.settings.shareJournalPdf]. Null in
     * tests / non-Android targets — the UI gates the export CTA on null and shows a
     * "not available" message instead.
     */
    val journalExport: (suspend () -> JournalExportResult)? = null,
    /**
     * Selects the daily bird for a given local date. Null = no candidates available.
     * Wired in MainActivity.buildAppGraph() via DailyBirdSelector.
     */
    val selectDailyBird: (suspend (kotlinx.datetime.LocalDate) -> se.birdy.domain.dailybird.DailyBird?)? = null,
    /**
     * Schedules push notifications via WorkManager + Android 13+ permission.
     * Null in non-Android targets.
     */
    val notificationScheduler: se.birdy.domain.notification.NotificationScheduler? = null,
    /**
     * Tracks which "Daily Bird" suggestions the user has matched (for daily_bird_hunter badge).
     */
    val dailyBirdHistory: se.birdy.data.dailybird.DailyBirdHistoryRepository? = null,
    /**
     * Platform-specific API for querying and opening system notification settings.
     * Null in non-Android targets or tests.
     */
    val platformNotificationsApi: se.birdy.app.notifications.PlatformNotificationsApi? = null,
    /**
     * Launches the Android 13+ POST_NOTIFICATIONS system permission dialog via the
     * Activity's registered ActivityResultContract. The Activity owns the result
     * callback — on grant it schedules workers and on either outcome it persists
     * pushPermissionAsked = true so the sheet doesn't reappear. Null in non-Android
     * targets or tests.
     */
    val requestPostNotificationsPermission: (() -> Unit)? = null,
    /**
     * Debug-only: enqueues a OneTimeWorkRequest for DailyBirdWorker. Wired only
     * when BuildConfig.DEBUG is true; null in release and on non-Android targets.
     */
    val devTriggerDailyBird: (() -> Unit)? = null,
    /**
     * Debug-only: enqueues a OneTimeWorkRequest for StreakRiskWorker. Wired only
     * when BuildConfig.DEBUG is true; null in release and on non-Android targets.
     */
    val devTriggerStreakRisk: (() -> Unit)? = null,
    /**
     * Emits `birdy://` deep-link URIs from MainActivity.onCreate/onNewIntent.
     * AppScaffold collects this flow and dispatches navigation.
     * Null in non-Android targets or tests.
     * replay = 1 so launches that emit before AppScaffold subscribes still get routed.
     */
    val deepLinkFlow: kotlinx.coroutines.flow.MutableSharedFlow<String>? = null,
) {
    val classifier: BirdClassifier
        get() =
            (classifierBootstrap.state.value as? ClassifierBootstrapState.Ready)?.classifier
                ?: error("Classifier not ready — AppGate should gate on bootstrap state")

    val classifierMode: ClassifierMode
        get() =
            (classifierBootstrap.state.value as? ClassifierBootstrapState.Ready)?.mode
                ?: ClassifierMode.DEMO

    val modelVersion: String?
        get() = (classifierBootstrap.state.value as? ClassifierBootstrapState.Ready)?.modelVersion

    private val recalculateBadges = RecalculateBadgesUseCase(clock = clock, zone = timeZone)

    val effectivePremiumActive: StateFlow<Boolean> by lazy {
        premiumRepository.state
            .map { backend -> (premiumOverride ?: backend) is PremiumState.Active }
            .stateIn(
                MainScope(),
                SharingStarted.Eagerly,
                (premiumOverride ?: premiumRepository.state.value) is PremiumState.Active,
            )
    }

    val badgeBackfill: BadgeBackfillOnAppStart by lazy {
        BadgeBackfillOnAppStart(
            recalc = recalculateBadges,
            obsRepo = observationRepository,
            speciesByQid = { repository.allByQid(defaultLocale) },
            badgeRepo = badgeRepository,
            catalog = badgeCatalog,
            versionStore = badgeVersionStore,
        )
    }

    val unlockQueue: se.birdy.app.ui.badges.UnlockQueue by lazy {
        se.birdy.app.ui.badges
            .UnlockQueue()
    }

    val premiumActivationListener: se.birdy.app.bootstrap.PremiumActivationListener by lazy {
        se.birdy.app.bootstrap.PremiumActivationListener(
            premiumActiveFlow = effectivePremiumActive,
            badgeRepo = badgeRepository,
            unlockQueue = unlockQueue,
            clock = clock,
        )
    }

    private val saveObservationUseCase: SaveObservationUseCase =
        SaveObservationUseCase(
            repo = observationRepository,
            badgeRepo = badgeRepository,
            photoStorage = photoStorage,
            clock = clock,
            catalog = badgeCatalog,
            recalculate = recalculateBadges,
            speciesByQid = { repository.allByQid(defaultLocale) },
            onObservationSaved =
                dailyBirdHistory?.let { history ->
                    { obs ->
                        val speciesId = obs.speciesId
                        if (speciesId != null) {
                            val today =
                                Clock.System
                                    .now()
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                    .date
                            val todayBird = history.speciesIdForDate(today)
                            if (todayBird == speciesId) {
                                history.markMatch(today, speciesId)
                            }
                        }
                    }
                },
            dailyBirdMatchCount = { dailyBirdHistory?.totalMatchCount() ?: 0 },
        )

    fun archiveViewModel(): ArchiveViewModel =
        ArchiveViewModel(
            repo = repository,
            observationRepo = observationRepository,
            prefs = userPreferences,
            locale = defaultLocale,
            premiumActiveFlow = effectivePremiumActive,
        )

    fun speciesProfileViewModel(speciesId: SpeciesId): SpeciesProfileViewModel =
        SpeciesProfileViewModel(repository, speciesId, defaultLocale)

    fun scanViewModel(): ScanViewModel =
        ScanViewModel(
            classifier = classifier,
            cameraSourceFactory = cameraSourceFactory,
            classifierMode = classifierMode,
        )

    fun photoAnalyzeViewModel(persist: (ByteArray) -> String): PhotoAnalyzeViewModel =
        PhotoAnalyzeViewModel(classifier = classifier, persist = persist)

    fun matchResultViewModel(
        sourceJson: String,
        capturedAtMs: Long,
    ): MatchResultViewModel {
        val source = Json.decodeFromString<ScanSourceSerialization>(sourceJson).toScanSource()
        return MatchResultViewModel(
            repository = repository,
            observationRepo = observationRepository,
            saveUseCase = saveObservationUseCase,
            catalog = badgeCatalog,
            source = source,
            capturedAtMs = capturedAtMs,
            locale = defaultLocale,
            matchOverrideReader = matchOverrideReader,
        )
    }

    fun badgesViewModel(): BadgesViewModel =
        BadgesViewModel(
            obsRepo = observationRepository,
            badgeRepo = badgeRepository,
            speciesByQid = { repository.allByQid(defaultLocale) },
            speciesTotalCount = repository.observeTotalCount(),
            catalog = badgeCatalog,
            recalc = recalculateBadges,
            zone = timeZone,
            locale = defaultLocale,
            premiumActiveFlow = effectivePremiumActive,
        )

    fun lifelistViewModel(): LifelistViewModel =
        LifelistViewModel(
            observationRepo = observationRepository,
            speciesRepo = repository,
            prefs = userPreferences,
            zone = timeZone,
            locale = defaultLocale,
        )

    fun observationDetailViewModel(id: String): ObservationDetailViewModel =
        ObservationDetailViewModel(
            id = id,
            obsRepo = observationRepository,
            speciesRepo = repository,
            photoStorage = photoStorage,
            locale = defaultLocale,
        )

    fun seasonStatsViewModel(): SeasonStatsViewModel =
        SeasonStatsViewModel(
            observationRepo = observationRepository,
            speciesRepo = repository,
            clock = clock,
            zone = timeZone,
            locale = defaultLocale,
        )

    fun settingsViewModel(): SettingsViewModel =
        SettingsViewModel(
            prefs = userPreferences,
            premiumRepository = premiumRepository,
            notificationScheduler = notificationScheduler,
            platformNotificationsApi = platformNotificationsApi,
            devTriggerDailyBird = devTriggerDailyBird,
            devTriggerStreakRisk = devTriggerStreakRisk,
        )

    fun premiumViewModel(): PremiumViewModel =
        PremiumViewModel(
            repository = premiumRepository,
            launchPurchase = launchPurchase ?: { premiumRepository.markPurchased(it) },
            formattedPricesFlow = formattedPricesFlow ?: MutableStateFlow(FormattedPrices()),
        )

    fun listenLauncherViewModel(): ListenLauncherViewModel =
        ListenLauncherViewModel(
            selectDailyBird = selectDailyBird,
            getSpeciesName = { qid -> repository.getById(SpeciesId(qid), defaultLocale).first()?.name },
            getSpeciesHeroPath = { qid ->
                repository
                    .getById(SpeciesId(qid), defaultLocale)
                    .first()
                    ?.images
                    ?.firstOrNull { it.role == "hero" }
                    ?.path
            },
            recordDailyBirdShown =
                dailyBirdHistory?.let { history ->
                    { date, sid -> history.recordToday(date, sid) }
                },
        )

    fun onboardingViewModel(
        fallbackName: String,
        isReplay: Boolean = false,
    ): OnboardingViewModel = OnboardingViewModel(prefs = userPreferences, defaultFallbackName = fallbackName, isReplay = isReplay)

    /**
     * Factory for [AudioScanViewModel].
     *
     * Requires [audioClassifierProvider], [audioStorageDir], [audioRecorderFactory],
     * and [waveformRendererFactory] to be non-null. On Android all four are injected
     * from [se.birdy.android.MainActivity]; in tests construct [AudioScanViewModel]
     * directly with fake collaborators instead.
     */
    fun audioScanViewModel(): AudioScanViewModel {
        val provider =
            audioClassifierProvider
                ?: error("audioClassifierProvider not wired — Plan 6b2 T3 must inject from MainActivity")
        val dir =
            audioStorageDir
                ?: error("audioStorageDir not wired — MainActivity must pass filesDir-derived path")
        val recorder =
            audioRecorderFactory?.invoke()
                ?: error("audioRecorderFactory not wired — MainActivity must inject AndroidAudioRecorderAdapter")
        val renderer =
            waveformRendererFactory?.invoke()
                ?: error("waveformRendererFactory not wired — MainActivity must inject AndroidWaveformRendererStub")
        return AudioScanViewModel(
            classifierProvider = provider,
            recorder = recorder,
            waveformRenderer = renderer,
            audioStorageDir = dir,
        )
    }
}
