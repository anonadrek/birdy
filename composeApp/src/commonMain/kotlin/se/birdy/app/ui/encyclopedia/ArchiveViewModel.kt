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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.SpeciesSummary
import se.birdy.datastore.ArchiveSort
import se.birdy.datastore.UserPreferences
import se.birdy.domain.observation.ObservationRepository

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ArchiveViewModel(
    private val repo: SpeciesRepository,
    private val observationRepo: ObservationRepository,
    private val prefs: UserPreferences,
    private val locale: Locale,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val chip: StateFlow<ArchiveChip> =
        prefs.archiveChip
            .map { runCatching { ArchiveChip.valueOf(it) }.getOrDefault(ArchiveChip.ALL) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ArchiveChip.ALL)

    val sort: StateFlow<ArchiveSort> =
        prefs.archiveSort
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ArchiveSort.ALPHA)

    private val stampNumbersBySpecies: StateFlow<Map<String, Int>> =
        observationRepo
            .observeAll()
            .map { observations ->
                observations
                    .filter { it.stampNumber > 0 }
                    .groupBy { it.speciesId }
                    .mapValues { (_, obs) -> obs.minOf { it.stampNumber } }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    val uiState: StateFlow<ArchiveUiState> =
        combine(
            _query.debounce(DEBOUNCE_MS).distinctUntilChanged(),
            chip,
            sort,
            stampNumbersBySpecies,
        ) { q, c, s, stamped -> Quad(q, c, s, stamped) }
            .flatMapLatest { (q, c, s, stamped) ->
                repo.search(q, locale, SpeciesFilter()).map { list -> toUiState(list, c, s, stamped) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ArchiveUiState.Loading)

    fun onQueryChanged(q: String) {
        _query.value = q
    }

    fun onChipSelected(c: ArchiveChip) {
        viewModelScope.launch { prefs.setArchiveChip(c.name) }
    }

    fun onSortToggle() {
        viewModelScope.launch {
            val current = prefs.archiveSort.first()
            val next =
                when (current) {
                    ArchiveSort.ALPHA -> ArchiveSort.FAMILY
                    ArchiveSort.FAMILY -> ArchiveSort.RECENT
                    ArchiveSort.RECENT -> ArchiveSort.ALPHA
                }
            prefs.setArchiveSort(next)
        }
    }

    private fun toUiState(
        list: List<SpeciesSummary>,
        c: ArchiveChip,
        s: ArchiveSort,
        stamped: Map<String, Int>,
    ): ArchiveUiState {
        val filtered =
            if (c == ArchiveChip.ALL) {
                list
            } else {
                val orders = ArchiveChip.orderSets[c].orEmpty()
                list.filter { it.iocOrder in orders }
            }
        if (filtered.isEmpty()) return ArchiveUiState.Empty
        val sorted =
            when (s) {
                ArchiveSort.ALPHA -> filtered.sortedBy { it.name.lowercase() }
                ArchiveSort.FAMILY -> filtered.sortedWith(compareBy({ it.family }, { it.name.lowercase() }))
                ArchiveSort.RECENT -> filtered
            }
        val rows =
            sorted.map {
                val stampNumber = stamped[it.id.raw]
                ArchiveRow(
                    summary = it,
                    isStamped = stampNumber != null,
                    stampNumber = stampNumber,
                )
            }
        return ArchiveUiState.Loaded(rows = rows, sort = s)
    }

    private data class Quad<A, B, C, D>(
        val a: A,
        val b: B,
        val c: C,
        val d: D,
    )

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}
