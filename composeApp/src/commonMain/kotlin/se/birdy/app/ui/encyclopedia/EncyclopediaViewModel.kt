package se.birdy.app.ui.encyclopedia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.SpeciesSummary

@OptIn(ExperimentalCoroutinesApi::class)
class EncyclopediaViewModel(
    private val repo: SpeciesRepository,
    private val locale: Locale,
) : ViewModel() {
    private val filter = MutableStateFlow(SpeciesFilter())

    val uiState: StateFlow<EncyclopediaUiState> =
        filter
            .flatMapLatest { f -> repo.all(locale).map { list -> applyFilter(list, f) to f } }
            .map { (list, f) -> toUiState(list, f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), EncyclopediaUiState.Loading)

    private fun applyFilter(
        list: List<SpeciesSummary>,
        f: SpeciesFilter,
    ): List<SpeciesSummary> =
        list.filter { sp ->
            f.abundance.isEmpty() || sp.abundance in f.abundance
        }

    private fun toUiState(
        list: List<SpeciesSummary>,
        f: SpeciesFilter,
    ): EncyclopediaUiState {
        if (list.isEmpty()) return EncyclopediaUiState.Empty
        val (common, others) = list.partition { it.abundance == Abundance.ALLMÄN }
        val header =
            if (f.regions.isEmpty() || "SE" in f.regions) {
                "Allmänna i Sverige"
            } else {
                "Allmänna"
            }
        return EncyclopediaUiState.Loaded(
            grouped = GroupedSpecies(common, others),
            sectionCommonHeader = header,
        )
    }
}
