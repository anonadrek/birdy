package se.birdy.app.ui.encyclopedia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.SpeciesSummary

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class EncyclopediaViewModel(
    private val repo: SpeciesRepository,
    private val locale: Locale,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SpeciesFilter())
    val filter: StateFlow<SpeciesFilter> = _filter.asStateFlow()

    val uiState: StateFlow<EncyclopediaUiState> =
        combine(
            _query.debounce(DEBOUNCE_MS).distinctUntilChanged(),
            _filter,
        ) { q, f -> q to f }
            .flatMapLatest { (q, f) -> repo.search(q, locale, f).map { list -> list to f } }
            .map { (list, f) -> toUiState(list, f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), EncyclopediaUiState.Loading)

    fun onQueryChanged(q: String) {
        _query.value = q
    }

    fun onFilterChanged(f: SpeciesFilter) {
        _filter.value = f
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

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}
