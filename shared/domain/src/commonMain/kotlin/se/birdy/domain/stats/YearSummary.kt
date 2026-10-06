package se.birdy.domain.stats

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.domain.badge.BadgeSeason
import se.birdy.domain.badge.seasonOfMonth
import se.birdy.domain.observation.Observation

private const val MONTHS_IN_YEAR = 12

/** A species' first find within one calendar year. */
data class FirstSighting(
    val speciesId: String,
    val capturedAt: Instant,
)

/**
 * One calendar year of finds, as the Season Statistics screen draws it (release 1.3.0 Task 7c):
 * finds per month for the year ring, and each species' first find of the year for the timeline
 * of firsts.
 *
 * [firstSightings] is a year list, not a life list: a species also seen in an earlier year still
 * gets its first find of THIS year.
 */
data class YearSummary(
    val year: Int,
    /** Finds per month, index 0 = January; always 12 entries. Finds of unknown species count too. */
    val findsPerMonth: List<Int>,
    /** Each identified species' first find of the year, earliest first (same instant: by species id). */
    val firstSightings: List<FirstSighting>,
) {
    val totalFinds: Int get() = findsPerMonth.sum()

    /**
     * The month (1..12) with the most finds, or null when the year has no finds or when two or
     * more months share the top count: the screen then names no "best month" rather than picking
     * one of the tied months arbitrarily.
     */
    val bestMonth: Int?
        get() {
            val top = findsPerMonth.maxOrNull() ?: return null
            if (top == 0 || findsPerMonth.count { it == top } > 1) return null
            return findsPerMonth.indexOf(top) + 1
        }

    /** Finds in the months of [season] this year (meteorological: December counts toward this year's winter). */
    fun findsIn(season: BadgeSeason): Int =
        findsPerMonth.withIndex().filter { (index, _) -> seasonOfMonth(index + 1) == season }.sumOf { it.value }
}

/**
 * Summarises the finds that fall in [year] as seen in [zone] (the zone decides which day, month
 * and year a find near midnight belongs to). Pure: no clock, no repository.
 */
fun summarizeYear(
    observations: List<Observation>,
    year: Int,
    zone: TimeZone,
): YearSummary {
    val findsPerMonth = IntArray(MONTHS_IN_YEAR)
    val firstBySpecies = mutableMapOf<String, Instant>()
    for (observation in observations) {
        val local = observation.capturedAt.toLocalDateTime(zone)
        if (local.year != year) continue
        findsPerMonth[local.monthNumber - 1]++
        observation.speciesId?.let { speciesId ->
            val earliest = firstBySpecies[speciesId]
            if (earliest == null || observation.capturedAt < earliest) {
                firstBySpecies[speciesId] = observation.capturedAt
            }
        }
    }
    val firstSightings =
        firstBySpecies
            .map { (speciesId, at) -> FirstSighting(speciesId, at) }
            .sortedWith(compareBy<FirstSighting> { it.capturedAt }.thenBy { it.speciesId })
    return YearSummary(year = year, findsPerMonth = findsPerMonth.toList(), firstSightings = firstSightings)
}
