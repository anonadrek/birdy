package se.birdy.app.ui.stats

/**
 * Plan 6b3 T9: state model for the Season Statistics screen.
 *
 * All counts are scoped to the current calendar year (resolved via Clock + TimeZone
 * in the ViewModel). The 12 month entries are always present so the year ring (and the
 * Lifelist's mini bar chart, [LiveStatsPreview]) has a stable January-to-December axis:
 * months with no observations are drawn as empty.
 *
 * Release 1.3.0 Task 7c (design option B, "the year ring and a journal of firsts"): the season
 * donut and the cumulative line are gone; [Loaded] carries the year, the best month and this
 * year's first sighting per species for the timeline, and [TopSpeciesRow] the plate photo for
 * the "Mest sedda" seals.
 */
sealed interface SeasonStatsUiState {
    data object Loading : SeasonStatsUiState

    /** No finds this year yet. [year] and [currentMonth] still let the screen draw an empty year ring. */
    data class Empty(
        val year: Int,
        val currentMonth: Int,
    ) : SeasonStatsUiState

    data class Loaded(
        val totalSpeciesThisYear: Int,
        val totalObservationsThisYear: Int,
        val monthBars: List<MonthBar>,
        val seasons: SeasonBreakdown,
        val topSpecies: List<TopSpeciesRow>,
        val firstSightings: List<FirstSightingRow>,
        val year: Int,
        /** 1..12, or null when no single month has the most finds (see `YearSummary.bestMonth`). */
        val bestMonth: Int?,
    ) : SeasonStatsUiState {
        val currentMonth: Int? get() = monthBars.firstOrNull { it.isCurrent }?.month
    }

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
        val heroImagePath: String? = null,
    )

    /**
     * One species' first find of the year: the [number]th new species of the year, first seen on
     * [dayOfMonth] [month].
     */
    data class FirstSightingRow(
        val qid: String,
        val nameLocalized: String,
        val heroImagePath: String?,
        val month: Int,
        val dayOfMonth: Int,
        val number: Int,
    )
}
