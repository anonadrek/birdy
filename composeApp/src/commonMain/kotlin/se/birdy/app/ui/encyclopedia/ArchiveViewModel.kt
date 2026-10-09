package se.birdy.app.ui.encyclopedia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.SpeciesSummary
import se.birdy.content.search.swedishSortKey
import se.birdy.datastore.ArchiveSort
import se.birdy.datastore.UserPreferences
import se.birdy.domain.observation.ObservationRepository

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ArchiveViewModel(
    private val repo: SpeciesRepository,
    private val observationRepo: ObservationRepository,
    private val prefs: UserPreferences,
    private val locale: Locale,
    premiumActiveFlow: Flow<Boolean> = flowOf(false),
) : ViewModel() {
    // Plan 6b3 T21 fix: source-of-truth is AppGraph.effectivePremiumActive (override
    // OR backend), not PremiumRepository.state alone — otherwise a premium override
    // (grandfathered user, spec §5.1, or a debug force) never opens the "Export Field
    // Journal" CTA on Archive. 1.3.1 ships with PREMIUM_OPEN_FOR_LAUNCH on (payment turns on
    // in a later release); after that the override path is still exercised by grandfathering.
    val premiumActive: StateFlow<Boolean> =
        premiumActiveFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

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
                    .asSequence()
                    .filter { it.stampNumber > 0 }
                    .mapNotNull { obs -> obs.speciesId?.let { it to obs.stampNumber } }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, stamps) -> stamps.min() }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    private val retryTick = MutableStateFlow(0)

    val uiState: StateFlow<ArchiveUiState> =
        combine(
            _query.debounce(DEBOUNCE_MS).distinctUntilChanged(),
            chip,
            sort,
            stampNumbersBySpecies,
            retryTick,
        ) { q, c, s, stamped, _ -> Quad(q, c, s, stamped) }
            .flatMapLatest { (q, c, s, stamped) ->
                repo
                    .search(q, locale, SpeciesFilter())
                    .map<List<SpeciesSummary>, ArchiveUiState> { list ->
                        toUiState(list, c, s, stamped, searching = q.isNotBlank())
                    }.catch { e -> emit(ArchiveUiState.Error(e.message)) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ArchiveUiState.Loading)

    fun onQueryChanged(q: String) {
        _query.value = q
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun retry() {
        retryTick.value += 1
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

    /**
     * [searching]: the repository returns search hits best match first (release 1.3.0 Task 7g,
     * SearchRanking). Then the name no longer breaks ties; the sorts below are stable, so within the
     * same family (FAMILY) or the same collection position (RECENT) and for A-Ö as a whole the
     * best matches stay on top. Without a query every sort falls back to the name, as before.
     */
    private fun toUiState(
        list: List<SpeciesSummary>,
        c: ArchiveChip,
        s: ArchiveSort,
        stamped: Map<String, Int>,
        searching: Boolean,
    ): ArchiveUiState {
        val filtered = list.filter { c.matches(it.group) }
        if (filtered.isEmpty()) return ArchiveUiState.Empty
        // Swedish alphabetical order (å, ä, ö after z): the pill says A-Ö.
        val byName: Comparator<SpeciesSummary> =
            if (searching) compareBy { 0 } else compareBy { swedishSortKey(it.name) }
        val sorted =
            when (s) {
                ArchiveSort.ALPHA -> filtered.sortedWith(byName)
                ArchiveSort.FAMILY -> filtered.sortedWith(compareBy<SpeciesSummary> { it.family }.then(byName))
                // "Recently added" = species most recently added to the user's collection first.
                // stamped[qid] holds the first stamp number for a species; higher = newer addition.
                // Unstamped species (not yet in the collection) sort last, alphabetically (best match
                // first while searching).
                ArchiveSort.RECENT ->
                    filtered.sortedWith(
                        compareByDescending<SpeciesSummary> { stamped[it.id.raw] ?: Int.MIN_VALUE }
                            .then(byName),
                    )
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
