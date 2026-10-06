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
import se.birdy.content.model.Species
import se.birdy.domain.badge.BadgeSeason
import se.birdy.domain.badge.seasonOf
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import se.birdy.domain.stats.YearSummary
import se.birdy.domain.stats.summarizeYear

/**
 * Plan 6b3 T9: aggregates the user's observations for the current calendar year into what
 * [SeasonStatsScreen] draws. Release 1.3.0 Task 7c: finds per month, the best month and each
 * species' first find of the year come from the domain's [summarizeYear]; the season sums and the
 * top three species are computed here as before.
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
            val now = clock.now().toLocalDateTime(zone)
            val summary = summarizeYear(allObservations, year = now.year, zone = zone)
            if (summary.totalFinds == 0) {
                _state.value = SeasonStatsUiState.Empty(year = now.year, currentMonth = now.monthNumber)
                return@launch
            }
            val thisYear = allObservations.filter { it.capturedAt.toLocalDateTime(zone).year == now.year }
            val speciesByQid = speciesRepo.allByQid(locale)
            _state.value = buildLoaded(summary, thisYear, speciesByQid, now.monthNumber)
        }
    }

    private fun buildLoaded(
        summary: YearSummary,
        thisYear: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
        currentMonth: Int,
    ): SeasonStatsUiState.Loaded {
        val monthBars =
            summary.findsPerMonth.mapIndexed { index, count ->
                val month = index + 1
                SeasonStatsUiState.MonthBar(
                    month = month,
                    label = monthLabel(month, locale),
                    observationCount = count,
                    isCurrent = month == currentMonth,
                )
            }
        val seasons =
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
                    val species = speciesByQid[SpeciesId(qid)]
                    SeasonStatsUiState.TopSpeciesRow(
                        qid = qid,
                        nameLocalized = species?.name ?: qid,
                        count = count,
                        heroImagePath = species?.heroImagePath(),
                    )
                }
        val firstSightings =
            summary.firstSightings.mapIndexed { index, sighting ->
                val species = speciesByQid[SpeciesId(sighting.speciesId)]
                val local = sighting.capturedAt.toLocalDateTime(zone)
                SeasonStatsUiState.FirstSightingRow(
                    qid = sighting.speciesId,
                    nameLocalized = species?.name ?: sighting.speciesId,
                    heroImagePath = species?.heroImagePath(),
                    month = local.monthNumber,
                    dayOfMonth = local.dayOfMonth,
                    number = index + 1,
                )
            }
        return SeasonStatsUiState.Loaded(
            totalSpeciesThisYear = summary.firstSightings.size,
            totalObservationsThisYear = summary.totalFinds,
            monthBars = monthBars,
            seasons = seasons,
            topSpecies = topSpecies,
            firstSightings = firstSightings,
            year = summary.year,
            bestMonth = summary.bestMonth,
        )
    }

    private fun Species.heroImagePath(): String? = images.firstOrNull { it.role == HERO_IMAGE_ROLE }?.path

    private fun monthLabel(
        month: Int,
        locale: Locale,
    ): String =
        when (locale) {
            Locale.SV -> SV_MONTHS[month - 1]
            Locale.EN -> EN_MONTHS[month - 1]
        }

    private companion object {
        /** "Mest sedda" shows the top three as photo seals (design option B). */
        const val TOP_SPECIES_LIMIT = 3
        const val HERO_IMAGE_ROLE = "hero"
        val SV_MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAJ", "JUN", "JUL", "AUG", "SEP", "OKT", "NOV", "DEC")
        val EN_MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    }
}
