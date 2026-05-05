package se.birdy.app.ui.result

import se.birdy.content.model.Species

data class ResolvedPrediction(
    val species: Species,
    val confidence: Float,
)

sealed interface ClassificationResultUiState {
    data object Loading : ClassificationResultUiState

    data class Loaded(
        val top1: ResolvedPrediction,
        val runnerUps: List<ResolvedPrediction>,
        val frozenFramePath: String?,
        val unresolved: List<String>,
    ) : ClassificationResultUiState

    data class Error(
        val kind: Kind,
    ) : ClassificationResultUiState {
        enum class Kind { NoPredictions, NoMatches }
    }
}
