package se.birdy.app.ui.diary

import se.birdy.content.model.Species
import se.birdy.domain.observation.Observation

sealed interface ObservationDetailUiState {
    data object Loading : ObservationDetailUiState

    data object NotFound : ObservationDetailUiState

    data class Loaded(
        val observation: Observation,
        val species: Species?,
        val noteSaving: Boolean = false,
    ) : ObservationDetailUiState

    data class Error(
        val kind: Kind,
    ) : ObservationDetailUiState {
        enum class Kind { LoadFailed, SaveNoteFailed, DeleteFailed }
    }
}

sealed interface DetailEffect {
    data class NoteSaved(
        val success: Boolean,
    ) : DetailEffect

    data object Deleted : DetailEffect
}
