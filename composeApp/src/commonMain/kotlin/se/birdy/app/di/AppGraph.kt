package se.birdy.app.di

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.photo.PhotoStorage
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
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.observation.ObservationRepository
import se.birdy.ml.BirdClassifier
import se.birdy.ml.CameraSource

class AppGraph(
    val repository: SpeciesRepository,
    val classifier: BirdClassifier,
    val cameraSourceFactory: () -> CameraSource,
    val observationRepository: ObservationRepository,
    val photoStorage: PhotoStorage,
    val badgeRepository: BadgeRepository,
    val badgeCatalog: BadgeCatalog,
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val defaultLocale: Locale = Locale.SV,
) {
    private val recalculateBadges = RecalculateBadgesUseCase(clock = clock, zone = timeZone)

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

    fun scanViewModel(): ScanViewModel = ScanViewModel(classifier = classifier, cameraSourceFactory = cameraSourceFactory)

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
            predictionsCsv = predictionsCsv,
            frameJpegPath = frameJpegPath,
            capturedAtMs = capturedAtMs,
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
