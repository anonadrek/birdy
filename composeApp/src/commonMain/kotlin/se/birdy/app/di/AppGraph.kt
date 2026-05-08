package se.birdy.app.di

import androidx.compose.runtime.Composable
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.bootstrap.BadgeBackfillOnAppStart
import se.birdy.app.bootstrap.BadgeVersionStore
import se.birdy.app.photo.PhotoStorage
import se.birdy.app.ui.badges.BadgesViewModel
import se.birdy.app.ui.diary.DiaryViewModel
import se.birdy.app.ui.diary.ObservationDetailViewModel
import se.birdy.app.ui.encyclopedia.EncyclopediaViewModel
import se.birdy.app.ui.photoanalyze.PhotoAnalyzeViewModel
import se.birdy.app.ui.profile.SpeciesProfileViewModel
import se.birdy.app.ui.result.ClassificationResultViewModel
import se.birdy.app.ui.scan.ScanViewModel
import se.birdy.app.usecase.SaveObservationUseCase
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.datastore.UserPreferences
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.observation.ObservationRepository
import se.birdy.ml.BirdClassifier
import se.birdy.ml.CameraSource
import se.birdy.ml.ClassifierMode

class AppGraph(
    val repository: SpeciesRepository,
    val classifier: BirdClassifier,
    val classifierMode: ClassifierMode = ClassifierMode.REAL,
    val cameraSourceFactory: () -> CameraSource,
    val observationRepository: ObservationRepository,
    val photoStorage: PhotoStorage,
    val badgeRepository: BadgeRepository,
    val badgeCatalog: BadgeCatalog,
    val badgeVersionStore: BadgeVersionStore,
    val userPreferences: UserPreferences,
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val defaultLocale: Locale = Locale.SV,
    /** Non-null only when the real TFLite model is loaded. Null in DEMO/fallback mode. */
    val modelVersion: String? = null,
    /**
     * Non-null = show debug overflow menu + register debug route.
     * Null = DEBUG features hidden (release builds, DEMO mode).
     * The composable lambda is androidMain-only; defined in MainActivity.
     */
    val benchmarkScreen: (@Composable () -> Unit)? = null,
) {
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

    fun encyclopediaViewModel(): EncyclopediaViewModel = EncyclopediaViewModel(repository, defaultLocale)

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

    fun classificationResultViewModel(
        predictionsCsv: String,
        frameJpegPath: String?,
        capturedAtMs: Long,
    ): ClassificationResultViewModel =
        ClassificationResultViewModel(
            repository = repository,
            saveUseCase = saveObservationUseCase,
            catalog = badgeCatalog,
            predictionsCsv = predictionsCsv,
            frameJpegPath = frameJpegPath,
            capturedAtMs = capturedAtMs,
            locale = defaultLocale,
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

    fun diaryViewModel(): DiaryViewModel =
        DiaryViewModel(
            obsRepo = observationRepository,
            speciesRepo = repository,
            locale = defaultLocale,
            clock = clock,
            timeZone = timeZone,
        )

    fun observationDetailViewModel(id: String): ObservationDetailViewModel =
        ObservationDetailViewModel(
            id = id,
            obsRepo = observationRepository,
            speciesRepo = repository,
            photoStorage = photoStorage,
            locale = defaultLocale,
        )
}
