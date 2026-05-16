package se.birdy.app.di

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.bootstrap.BadgeBackfillOnAppStart
import se.birdy.app.bootstrap.BadgeVersionStore
import se.birdy.app.data.premium.FormattedPrices
import se.birdy.app.photo.PhotoStorage
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
import se.birdy.ml.BirdClassifier
import se.birdy.ml.CameraSource
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.ClassifierBootstrapState
import se.birdy.ml.ClassifierMode

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

    val badgeBackfill: BadgeBackfillOnAppStart by lazy {
        BadgeBackfillOnAppStart(
            recalc = recalculateBadges,
            obsRepo = observationRepository,
            speciesByQid = { repository.allByQid() },
            badgeRepo = badgeRepository,
            catalog = badgeCatalog,
            versionStore = badgeVersionStore,
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
            speciesByQid = { repository.allByQid() },
        )

    fun archiveViewModel(): ArchiveViewModel = ArchiveViewModel(repository, observationRepository, userPreferences, defaultLocale)

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
        predictionsCsv: String,
        frameJpegPath: String?,
        capturedAtMs: Long,
    ): MatchResultViewModel =
        MatchResultViewModel(
            repository = repository,
            observationRepo = observationRepository,
            saveUseCase = saveObservationUseCase,
            catalog = badgeCatalog,
            predictionsCsv = predictionsCsv,
            frameJpegPath = frameJpegPath,
            capturedAtMs = capturedAtMs,
            locale = defaultLocale,
            matchOverrideReader = matchOverrideReader,
        )

    fun badgesViewModel(): BadgesViewModel =
        BadgesViewModel(
            obsRepo = observationRepository,
            badgeRepo = badgeRepository,
            speciesByQid = { repository.allByQid() },
            speciesTotalCount = repository.observeTotalCount(),
            catalog = badgeCatalog,
            recalc = recalculateBadges,
            zone = timeZone,
            locale = defaultLocale,
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

    fun settingsViewModel(): SettingsViewModel = SettingsViewModel(userPreferences, premiumRepository)

    fun premiumViewModel(): PremiumViewModel =
        PremiumViewModel(
            repository = premiumRepository,
            launchPurchase = launchPurchase ?: { premiumRepository.markPurchased(it) },
            formattedPricesFlow = formattedPricesFlow ?: MutableStateFlow(FormattedPrices()),
        )

    fun listenLauncherViewModel(): ListenLauncherViewModel = ListenLauncherViewModel()

    fun onboardingViewModel(fallbackName: String): OnboardingViewModel =
        OnboardingViewModel(prefs = userPreferences, defaultFallbackName = fallbackName)
}
