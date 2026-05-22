package se.birdy.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.domain.badge.BadgeSeason
import se.birdy.domain.badge.seasonOf
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository

/**
 * Plan 6b3 T9: aggregates the user's observations for the current calendar year
 * into the four sections rendered by [SeasonStatsScreen] — month bars, season
 * donut, top-5 species, cumulative new-species line.
 *
 * Aggregation runs lazily via [onEnter]; the screen calls it once from a
 * LaunchedEffect, the ViewModel snapshots `observationRepo.observeAll().first()`
 * and emits Loaded/Empty. No further observation of the Flow — the page is a
 * single-snapshot view, not a live dashboard.
 */
class SeasonStatsViewModel(
    private val observationRepo: ObservationRepository,
    private val speciesRepo: SpeciesRepository,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val locale: Locale = Locale.SV,
) : ViewModel() {
    private val _state = MutableStateFlow<SeasonStatsUiState>(SeasonStatsUiState.Loading)
    val state: StateFlow<SeasonStatsUiState> = _state.asStateFlow()

    fun onEnter() {
        viewModelScope.launch {
            val allObservations = observationRepo.observeAll().first()
            val currentYear = clock.now().toLocalDateTime(zone).year
            val thisYear = allObservations.filter { it.capturedAt.toLocalDateTime(zone).year == currentYear }
            if (thisYear.isEmpty()) {
                _state.value = SeasonStatsUiState.Empty
                return@launch
            }
            val speciesByQid = speciesRepo.allByQid(locale)
            val currentMonth = clock.now().toLocalDateTime(zone).monthNumber
            _state.value = buildLoaded(thisYear, speciesByQid, currentMonth)
        }
    }

    private fun buildLoaded(
        thisYear: List<Observation>,
        speciesByQid: Map<SpeciesId, se.birdy.content.model.Species>,
        currentMonth: Int,
    ): SeasonStatsUiState.Loaded {
        val byMonth = thisYear.groupBy { it.capturedAt.toLocalDateTime(zone).monthNumber }
        val monthBars =
            (1..12).map { m ->
                SeasonStatsUiState.MonthBar(
                    month = m,
                    label = monthLabel(m, locale),
                    observationCount = byMonth[m].orEmpty().size,
                    isCurrent = m == currentMonth,
                )
            }
        val seasonDonut =
            SeasonStatsUiState.SeasonBreakdown(
                winter = thisYear.count { seasonOf(it.capturedAt, zone) == BadgeSeason.WINTER },
                spring = thisYear.count { seasonOf(it.capturedAt, zone) == BadgeSeason.SPRING },
                summer = thisYear.count { seasonOf(it.capturedAt, zone) == BadgeSeason.SUMMER },
                autumn = thisYear.count { seasonOf(it.capturedAt, zone) == BadgeSeason.AUTUMN },
            )
        val topSpecies =
            thisYear
                .mapNotNull { it.speciesId }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(TOP_SPECIES_LIMIT)
                .map { (qid, count) ->
                    SeasonStatsUiState.TopSpeciesRow(
                        qid = qid,
                        nameLocalized = speciesByQid[SpeciesId(qid)]?.name ?: qid,
                        count = count,
                    )
                }
        val cumulativeLine = buildCumulativeLine(thisYear)
        return SeasonStatsUiState.Loaded(
            totalSpeciesThisYear = thisYear.mapNotNull { it.speciesId }.toSet().size,
            totalObservationsThisYear = thisYear.size,
            monthBars = monthBars,
            seasonDonut = seasonDonut,
            topSpecies = topSpecies,
            cumulativeLine = cumulativeLine,
        )
    }

    private fun buildCumulativeLine(thisYear: List<Observation>): List<SeasonStatsUiState.CumulativePoint> {
        val seenByMonth = mutableMapOf<Int, MutableSet<String>>()
        for (obs in thisYear) {
            val qid = obs.speciesId ?: continue
            val month = obs.capturedAt.toLocalDateTime(zone).monthNumber
            seenByMonth.getOrPut(month) { mutableSetOf() }.add(qid)
        }
        val running = mutableSetOf<String>()
        return (1..12).map { m ->
            running.addAll(seenByMonth[m].orEmpty())
            SeasonStatsUiState.CumulativePoint(month = m, uniqueSpeciesByEndOfMonth = running.size)
        }
    }

    private fun monthLabel(
        month: Int,
        locale: Locale,
    ): String =
        when (locale) {
            Locale.SV -> SV_MONTHS[month - 1]
            Locale.EN -> EN_MONTHS[month - 1]
        }

    private companion object {
        const val TOP_SPECIES_LIMIT = 5
        val SV_MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAJ", "JUN", "JUL", "AUG", "SEP", "OKT", "NOV", "DEC")
        val EN_MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    }
}
