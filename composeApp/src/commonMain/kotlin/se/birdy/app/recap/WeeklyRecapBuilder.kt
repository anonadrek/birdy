package se.birdy.app.recap

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.currentWeeklyStreak
import se.birdy.domain.badge.weekKey
import se.birdy.domain.observation.Observation

class WeeklyRecapBuilder(
    private val zone: TimeZone,
) {
    fun summarize(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        now: Instant,
    ): WeeklyRecapSummary {
        val current = weekKey(now, zone)
        val prev = current.prev()
        val thisWeekCount = observations.count { weekKey(it.capturedAt, zone) == current }
        val lastWeekCount = observations.count { weekKey(it.capturedAt, zone) == prev }

        val firstBySpeciesId =
            observations.asSequence()
                .filter { it.speciesId != null }
                .groupBy { it.speciesId!! }
                .mapValues { (_, obs) -> obs.minOf { it.capturedAt } }
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
}
