package se.birdy.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository

class SpeciesProfileViewModel(
    repo: SpeciesRepository,
    speciesId: SpeciesId,
    locale: Locale,
) : ViewModel() {
    val uiState: StateFlow<SpeciesProfileUiState> =
        repo
            .getById(speciesId, locale)
            .map { species ->
                if (species == null) {
                    SpeciesProfileUiState.NotFound
                } else {
                    SpeciesProfileUiState.Loaded(species)
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000L),
                SpeciesProfileUiState.Loading,
            )
}
