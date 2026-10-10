package se.birdy.app.recap

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.WeekKey
import se.birdy.domain.badge.currentWeeklyStreak
import se.birdy.domain.badge.weekKey
import se.birdy.domain.observation.Observation

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

    /** The current week's numbers (the Sunday notification). */
    fun summarize(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        now: Instant,
    ): WeeklyRecapSummary {
        val firstBySpeciesId = firstSightingBySpeciesId(observations)
        return summarize(observations, unlocks, now, weekKey(now, zone), firstBySpeciesId)
    }

    private fun summarize(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        now: Instant,
        week: WeekKey,
        firstBySpeciesId: Map<String, Instant>,
    ): WeeklyRecapSummary {
        val thisWeekCount = observations.count { weekKey(it.capturedAt, zone) == week }
        val lastWeekCount = observations.count { weekKey(it.capturedAt, zone) == week.prev() }
        val newSpeciesCount = firstBySpeciesId.count { weekKey(it.value, zone) == week }

        // An earlier week (the notification opened after midnight) counts its streak as it stood
        // then, and its streak can no longer be "at risk": its Sunday is over.
        val isCurrentWeek = week == weekKey(now, zone)
        val asOf = if (isCurrentWeek) now else week.monday().atStartOfDayIn(zone)
        val streak = currentWeeklyStreak(observations.map { it.capturedAt }, zone, asOf)
        val streakAtRisk = isCurrentWeek && thisWeekCount == 0 && streak >= 2

        return WeeklyRecapSummary(
            week = week,
            observationCount = thisWeekCount,
            newSpeciesCount = newSpeciesCount,
            newBadgeIds = unlocks.filter { weekKey(it.unlockedAt, zone) == week }.map { it.badgeId },
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
        val firstBySpeciesId = firstSightingBySpeciesId(observations)
        return selectFinds(observations, speciesByQid, weekKey(now, zone), firstBySpeciesId)
    }

    private fun selectFinds(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, SpeciesSummary>,
        week: WeekKey,
        firstBySpeciesId: Map<String, Instant>,
    ): List<HeroFind> {
        fun isNew(o: Observation): Boolean {
            val first = o.speciesId?.let { firstBySpeciesId[it] } ?: return false
            return weekKey(first, zone) == week
        }

        return observations
            .filter { weekKey(it.capturedAt, zone) == week }
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
     * The seven days of [week], Monday first, each with its finds (release 1.3.0 Task 7j). Days
     * are local dates: a find belongs to the day the user saw it in [zone], so the week still ends
     * at Sunday midnight in the weeks the clocks change (167 or 169 hours long).
     */
    private fun weekDays(
        finds: List<HeroFind>,
        week: WeekKey,
        now: Instant,
    ): List<RecapDay> {
        val today = now.toLocalDateTime(zone).date
        val monday = week.monday()
        // `finds` is newest first, so the first find of a day is its latest.
        val findsByDay = finds.groupBy { it.capturedAt.toLocalDateTime(zone).date }
        return (0 until DAYS_IN_WEEK).map { offset ->
            val date: LocalDate = monday.plus(offset, DateTimeUnit.DAY)
            val dayFinds = findsByDay[date].orEmpty()
            RecapDay(date = date, findCount = dayFinds.size, cover = dayFinds.firstOrNull(), isFuture = date > today)
        }
    }

    /** The week's new species, latest addition to the life list first (release 1.3.0 Task 7j). */
    private fun newSpecies(
        firstBySpeciesId: Map<String, Instant>,
        finds: List<HeroFind>,
    ): List<NewSpecies> {
        val numbers = lifeListNumbers(firstBySpeciesId)
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

    /**
     * The recap of [week], the current week unless asked for another (the Sunday notification
     * names its week, so a tap after midnight still opens the week it described). The first
     * sightings are computed once and shared by the numbers, the finds and the new species.
     */
    fun build(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, SpeciesSummary>,
        unlocks: List<BadgeUnlock>,
        now: Instant,
        week: WeekKey = weekKey(now, zone),
    ): WeeklyRecap {
        val firstBySpeciesId = firstSightingBySpeciesId(observations)
        val finds = selectFinds(observations, speciesByQid, week, firstBySpeciesId)
        return WeeklyRecap(
            summary = summarize(observations, unlocks, now, week, firstBySpeciesId),
            finds = finds,
            days = weekDays(finds, week, now),
            newSpecies = newSpecies(firstBySpeciesId, finds),
        )
    }
}
