package se.birdy.app.ui.stats

/**
 * Plan 6b3 T9: state model for the Season Statistics screen.
 *
 * All counts are scoped to the current calendar year (resolved via Clock + TimeZone
 * in the ViewModel). The 12 month-bars are always present so the chart axis is
 * stable from January to December — months with no observations render as
 * zero-height bars.
 */
sealed interface SeasonStatsUiState {
    data object Loading : SeasonStatsUiState

    data object Empty : SeasonStatsUiState

    data class Loaded(
        val totalSpeciesThisYear: Int,
        val totalObservationsThisYear: Int,
        val monthBars: List<MonthBar>,
        val seasonDonut: SeasonBreakdown,
        val topSpecies: List<TopSpeciesRow>,
        val cumulativeLine: List<CumulativePoint>,
    ) : SeasonStatsUiState

    data class MonthBar(
        val month: Int,
        val label: String,
        val observationCount: Int,
        val isCurrent: Boolean,
    )

    data class SeasonBreakdown(
        val winter: Int,
        val spring: Int,
        val summer: Int,
        val autumn: Int,
    ) {
        val total: Int get() = winter + spring + summer + autumn
    }

    data class TopSpeciesRow(
        val qid: String,
        val nameLocalized: String,
        val count: Int,
    )

    data class CumulativePoint(
        val month: Int,
        val uniqueSpeciesByEndOfMonth: Int,
    )
}
