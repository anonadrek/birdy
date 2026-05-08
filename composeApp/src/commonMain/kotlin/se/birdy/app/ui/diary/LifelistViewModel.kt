package se.birdy.app.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.Species
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice
import se.birdy.datastore.UserPreferences
import se.birdy.domain.badge.longestWeeklyStreak
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository

@OptIn(ExperimentalCoroutinesApi::class)
class LifelistViewModel(
    private val observationRepo: ObservationRepository,
    private val speciesRepo: SpeciesRepository,
    private val prefs: UserPreferences,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val locale: Locale = Locale.SV,
) : ViewModel() {
    val uiState: StateFlow<LifelistUiState> =
        combine(
            observationRepo.observeAll(),
            prefs.userName,
            prefs.lifelistStat3,
            prefs.lifelistSort,
        ) { obs, name, stat3, sort ->
            if (obs.isEmpty()) {
                LifelistUiState.Empty
            } else {
                val byQid = speciesRepo.allByQid()
                buildLoaded(obs, byQid, name.ifEmpty { defaultName() }, stat3, sort)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LifelistUiState.Loading)

    fun onStat3Toggle() {
        viewModelScope.launch {
            val next =
                when (prefs.lifelistStat3.first()) {
                    LifelistStat3Choice.STREAK -> LifelistStat3Choice.SPECIES_THIS_YEAR
                    LifelistStat3Choice.SPECIES_THIS_YEAR -> LifelistStat3Choice.SPECIES_THIS_MONTH
                    LifelistStat3Choice.SPECIES_THIS_MONTH -> LifelistStat3Choice.LONGEST_STREAK
                    LifelistStat3Choice.LONGEST_STREAK -> LifelistStat3Choice.STREAK
                }
            prefs.setLifelistStat3(next)
        }
    }

    fun onSortToggle() {
        viewModelScope.launch {
            val next =
                when (prefs.lifelistSort.first()) {
                    LifelistSort.RECENT -> LifelistSort.STAMP_NUMBER
                    LifelistSort.STAMP_NUMBER -> LifelistSort.SPECIES
                    LifelistSort.SPECIES -> LifelistSort.RECENT
                }
            prefs.setLifelistSort(next)
        }
    }

    private fun buildLoaded(
        obs: List<Observation>,
        byQid: Map<SpeciesId, Species>,
        name: String,
        stat3: LifelistStat3Choice,
        sort: LifelistSort,
    ): LifelistUiState.Loaded {
        val rows =
            obs
                .map { o ->
                    LifelistRow(
                        observation = o,
                        species = byQid[SpeciesId(o.speciesId)],
                    )
                }.let { list ->
                    when (sort) {
                        LifelistSort.RECENT -> list.sortedByDescending { it.observation.savedAt }
                        LifelistSort.STAMP_NUMBER -> list.sortedByDescending { it.observation.stampNumber }
                        LifelistSort.SPECIES ->
                            list.sortedWith(compareBy { it.species?.name ?: it.observation.speciesId })
                    }
                }
        return LifelistUiState.Loaded(
            userName = name,
            speciesCount = obs.map { it.speciesId }.toSet().size,
            stampsCount = obs.size,
            stat3 = computeStat3(obs, stat3),
            sort = sort,
            rows = rows,
        )
    }

    private fun computeStat3(
        obs: List<Observation>,
        choice: LifelistStat3Choice,
    ): Stat3Value {
        // TODO(plan-7c): wire real time-window filtering for SPECIES_THIS_YEAR / SPECIES_THIS_MONTH.
        // For Plan 7b, all distinct species count is acceptable placeholder.
        return when (choice) {
            LifelistStat3Choice.STREAK ->
                Stat3Value(
                    kind = LifelistStat3Choice.STREAK,
                    value = longestWeeklyStreak(obs.map { it.capturedAt }, zone),
                )
            LifelistStat3Choice.SPECIES_THIS_YEAR ->
                Stat3Value(
                    kind = LifelistStat3Choice.SPECIES_THIS_YEAR,
                    value = obs.distinctBy { it.speciesId }.size,
                )
            LifelistStat3Choice.SPECIES_THIS_MONTH ->
                Stat3Value(
                    kind = LifelistStat3Choice.SPECIES_THIS_MONTH,
                    value = obs.distinctBy { it.speciesId }.size,
                )
            LifelistStat3Choice.LONGEST_STREAK ->
                Stat3Value(
                    kind = LifelistStat3Choice.LONGEST_STREAK,
                    value = longestWeeklyStreak(obs.map { it.capturedAt }, zone),
                )
        }
    }

    private fun defaultName(): String = if (locale == Locale.SV) "Min" else "My"
}
