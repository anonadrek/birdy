package se.birdy.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository

/**
 * [onOpened] runs once when the profile opens (release 1.3.0 Task 7d: opening today's bird hides
 * the Identify tab's dot). A failure there is logged and never affects the profile itself.
 */
class SpeciesProfileViewModel(
    repo: SpeciesRepository,
    speciesId: SpeciesId,
    locale: Locale,
    onOpened: (suspend () -> Unit)? = null,
) : ViewModel() {
    init {
        if (onOpened != null) viewModelScope.launch { reportOpened(onOpened) }
    }

    // A side effect only: any failure is logged and the profile still shows.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun reportOpened(onOpened: suspend () -> Unit) {
        try {
            onOpened()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            println("SpeciesProfileViewModel: onOpened failed: ${t.message}")
        }
    }

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
