package se.birdy.app.recap

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.currentWeeklyStreak
import se.birdy.domain.badge.weekKey
import se.birdy.domain.observation.Observation

private const val DAYS_IN_WEEK = 7

class WeeklyRecapBuilder(
    private val zone: TimeZone,
) {
    /** Maps each speciesId to the earliest captured Instant across all observations. */
    private fun firstSightingBySpeciesId(observations: List<Observation>): Map<String, Instant> =
        observations
            .asSequence()
            .filter { it.speciesId != null }
            .groupBy { it.speciesId!! }
            .mapValues { (_, obs) -> obs.minOf { it.capturedAt } }

    /**
     * Each species' place in the life list: the order of first sightings, 1 for the first species
     * ever found. Two species first seen at the same instant are ordered by species id, as the
     * season statistics order their year list (`summarizeYear`).
     */
    private fun lifeListNumbers(firstBySpeciesId: Map<String, Instant>): Map<String, Int> =
        firstBySpeciesId.entries
            .sortedWith(compareBy<Map.Entry<String, Instant>> { it.value }.thenBy { it.key })
            .withIndex()
            .associate { (index, entry) -> entry.key to index + 1 }

    fun summarize(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        now: Instant,
    ): WeeklyRecapSummary {
        val current = weekKey(now, zone)
        val prev = current.prev()
        val thisWeekCount = observations.count { weekKey(it.capturedAt, zone) == current }
        val lastWeekCount = observations.count { weekKey(it.capturedAt, zone) == prev }

        val firstBySpeciesId = firstSightingBySpeciesId(observations)
        val newSpeciesCount = firstBySpeciesId.count { weekKey(it.value, zone) == current }

        val streak = currentWeeklyStreak(observations.map { it.capturedAt }, zone, now)
        val streakAtRisk = thisWeekCount == 0 && streak >= 2

        return WeeklyRecapSummary(
            week = current,
            observationCount = thisWeekCount,
            newSpeciesCount = newSpeciesCount,
            newBadgeIds = unlocks.filter { weekKey(it.unlockedAt, zone) == current }.map { it.badgeId },
            weeklyStreak = streak,
            deltaVsLastWeek = thisWeekCount - lastWeekCount,
            streakAtRisk = streakAtRisk,
        )
    }

    /** Veckans alla fynd, nyaste först. Tom lista vid tyst vecka. */
    fun selectFinds(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, SpeciesSummary>,
        now: Instant,
    ): List<HeroFind> {
        val current = weekKey(now, zone)
        val firstBySpeciesId = firstSightingBySpeciesId(observations)

        fun isNew(o: Observation): Boolean =
            o.speciesId != null && firstBySpeciesId[o.speciesId]?.let { weekKey(it, zone) == current } == true

        return observations
            .filter { weekKey(it.capturedAt, zone) == current }
            .sortedByDescending { it.capturedAt }
            .map { o ->
                val summary = o.speciesId?.let { speciesByQid[SpeciesId(it)] }
                HeroFind(
                    observationId = o.id,
                    speciesId = o.speciesId,
                    photoPath = o.photoPath,
                    heroImagePath = summary?.heroImagePath,
                    isNewSpecies = isNew(o),
                    capturedAt = o.capturedAt,
                    source = o.sourceType,
                )
            }
    }

    /**
     * The seven days of the week of [now], Monday first, each with its finds (release 1.3.0 Task
     * 7j). Days are local dates: a find belongs to the day the user saw it in [zone], so the week
     * still ends at Sunday midnight in the weeks the clocks change (167 or 169 hours long).
     */
    fun weekDays(
        finds: List<HeroFind>,
        now: Instant,
    ): List<RecapDay> {
        val today = now.toLocalDateTime(zone).date
        val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        // `finds` is newest first, so the first find of a day is its latest.
        val findsByDay = finds.groupBy { it.capturedAt.toLocalDateTime(zone).date }
        return (0 until DAYS_IN_WEEK).map { offset ->
            val date: LocalDate = monday.plus(offset, DateTimeUnit.DAY)
            val dayFinds = findsByDay[date].orEmpty()
            RecapDay(date = date, findCount = dayFinds.size, cover = dayFinds.firstOrNull(), isFuture = date > today)
        }
    }

    /** This week's new species, latest addition to the life list first (release 1.3.0 Task 7j). */
    fun newSpecies(
        observations: List<Observation>,
        finds: List<HeroFind>,
    ): List<NewSpecies> {
        val numbers = lifeListNumbers(firstSightingBySpeciesId(observations))
        return finds
            .filter { it.isNewSpecies && it.speciesId != null }
            .groupBy { it.speciesId!! }
            .map { (speciesId, speciesFinds) ->
                NewSpecies(
                    speciesId = speciesId,
                    lifeListNumber = numbers.getValue(speciesId),
                    // `finds` is newest first: the last one is the first sighting.
                    firstFind = speciesFinds.last(),
                )
            }.sortedByDescending { it.lifeListNumber }
    }

    fun build(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, SpeciesSummary>,
        unlocks: List<BadgeUnlock>,
        now: Instant,
    ): WeeklyRecap {
        val finds = selectFinds(observations, speciesByQid, now)
        return WeeklyRecap(
            summary = summarize(observations, unlocks, now),
            finds = finds,
            days = weekDays(finds, now),
            newSpecies = newSpecies(observations, finds),
        )
    }
}
