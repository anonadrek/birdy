package se.birdy.app.di

import se.birdy.app.ui.encyclopedia.EncyclopediaViewModel
import se.birdy.app.ui.photoanalyze.PhotoAnalyzeViewModel
import se.birdy.app.ui.profile.SpeciesProfileViewModel
import se.birdy.app.ui.scan.ScanViewModel
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.ml.BirdClassifier
import se.birdy.ml.CameraSource

class AppGraph(
    val repository: SpeciesRepository,
    val classifier: BirdClassifier,
    val cameraSourceFactory: () -> CameraSource,
    val defaultLocale: Locale = Locale.SV,
) {
    fun encyclopediaViewModel(): EncyclopediaViewModel = EncyclopediaViewModel(repository, defaultLocale)

    fun speciesProfileViewModel(speciesId: SpeciesId): SpeciesProfileViewModel =
        SpeciesProfileViewModel(repository, speciesId, defaultLocale)

    fun scanViewModel(): ScanViewModel =
        ScanViewModel(
            classifier = classifier,
            cameraSourceFactory = cameraSourceFactory,
        )

    fun photoAnalyzeViewModel(persist: (ByteArray) -> String): PhotoAnalyzeViewModel =
        PhotoAnalyzeViewModel(classifier = classifier, persist = persist)
}
