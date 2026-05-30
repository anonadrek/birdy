package se.birdy.app.recap

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.domain.badge.BadgeUnlock
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

    fun selectHero(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, SpeciesSummary>,
        now: Instant,
    ): HeroFind? {
        val current = weekKey(now, zone)
        val thisWeek = observations.filter { weekKey(it.capturedAt, zone) == current }
        if (thisWeek.isEmpty()) return null

        val firstBySpeciesId = firstSightingBySpeciesId(observations)

        fun isNew(o: Observation): Boolean =
            o.speciesId != null && firstBySpeciesId[o.speciesId]?.let { weekKey(it, zone) == current } == true

        val chosen =
            thisWeek.filter { isNew(it) }.maxByOrNull { it.capturedAt }
                ?: thisWeek
                    .filter { it.speciesId != null }
                    .maxWithOrNull(
                        compareBy<Observation> {
                            speciesByQid[SpeciesId(it.speciesId!!)]?.abundance?.ordinal ?: -1
                        }.thenBy { it.capturedAt },
                    )
                ?: thisWeek.maxByOrNull { it.capturedAt }
                ?: return null

        val summary = chosen.speciesId?.let { speciesByQid[SpeciesId(it)] }
        return HeroFind(
            observationId = chosen.id,
            speciesId = chosen.speciesId,
            photoPath = chosen.photoPath,
            heroImagePath = summary?.heroImagePath,
            isNewSpecies = isNew(chosen),
        )
    }

    fun build(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, SpeciesSummary>,
        unlocks: List<BadgeUnlock>,
        now: Instant,
    ): WeeklyRecap =
        WeeklyRecap(
            summary = summarize(observations, unlocks, now),
            hero = selectHero(observations, speciesByQid, now),
        )
}
