package se.birdy.app.ui.scaffold

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable data object Scan : AppRoute

    @Serializable data object PhotoAnalyze : AppRoute

    @Serializable data class ClassificationResult(
        val predictionsCsv: String,
        val frameJpegPath: String?,
    ) : AppRoute

    @Serializable data object Encyclopedia : AppRoute

    @Serializable data object EncyclopediaList : AppRoute

    @Serializable data class SpeciesProfile(
        val speciesId: String,
    ) : AppRoute

    @Serializable data object Diary : AppRoute

    @Serializable data object Badges : AppRoute
}
