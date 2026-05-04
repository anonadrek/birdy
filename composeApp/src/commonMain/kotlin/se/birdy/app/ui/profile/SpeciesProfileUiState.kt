package se.birdy.app.ui.profile

import se.birdy.content.model.Species

sealed interface SpeciesProfileUiState {
    data object Loading : SpeciesProfileUiState

    data class Loaded(
        val species: Species,
    ) : SpeciesProfileUiState

    data object NotFound : SpeciesProfileUiState
}
