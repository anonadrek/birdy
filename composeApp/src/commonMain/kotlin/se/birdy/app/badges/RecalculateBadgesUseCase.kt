package se.birdy.app.badges

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.consecutiveSundaysWithObservations
import se.birdy.domain.badge.longestMonthlyStreak
import se.birdy.domain.badge.longestWeeklyStreak
import se.birdy.domain.badge.seasonOf
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationSource

private val RED_LISTED = setOf("NT", "VU", "CR")

class RecalculateBadgesUseCase(
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun newUnlocks(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
        catalog: BadgeCatalog,
        existingUnlocks: Set<String>,
        dailyBirdMatchCount: Int = 0,
    ): List<BadgeUnlock> {
        val now = clock.now()
        return catalog.badges
            .filter { it.id !in existingUnlocks }
            .filter { it.rule !is BadgeRule.Manual }
            .filter { evaluate(it.rule, observations, speciesByQid, dailyBirdMatchCount) }
            .map { BadgeUnlock(it.id, now) }
    }

    fun currentValue(
        rule: BadgeRule,
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
        dailyBirdMatchCount: Int = 0,
    ): Int = rawValue(rule, observations, speciesByQid, dailyBirdMatchCount).coerceAtMost(rule.target)

    private fun evaluate(
        rule: BadgeRule,
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
        dailyBirdMatchCount: Int = 0,
    ): Boolean = rawValue(rule, observations, speciesByQid, dailyBirdMatchCount) >= rule.target

    private fun rawValue(
        rule: BadgeRule,
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
        dailyBirdMatchCount: Int = 0,
    ): Int =
        when (rule) {
            is BadgeRule.CountUniqueSpecies ->
                observations.mapNotNull { it.speciesId }.toSet().size
            is BadgeRule.WeeklyStreak -> longestWeeklyStreak(observations.map { it.capturedAt }, zone)
            is BadgeRule.MonthlyStreak -> longestMonthlyStreak(observations.map { it.capturedAt }, zone)
            is BadgeRule.ObservedInSeason ->
                observations.count { seasonOf(it.capturedAt, zone) == rule.season }
            is BadgeRule.ObservedInFamily ->
                observations.mapNotNull { it.speciesId }.distinct().count { qid ->
                    speciesByQid[SpeciesId(qid)]?.taxonomy?.family == rule.family
                }
            is BadgeRule.ObservedInFamilyGroup ->
                observations.mapNotNull { it.speciesId }.distinct().count { qid ->
                    speciesByQid[SpeciesId(qid)]?.taxonomy?.family in rule.families
                }
            is BadgeRule.CountDistinctFamilies ->
                observations.mapNotNull { it.speciesId }
                    .mapNotNull { speciesByQid[SpeciesId(it)]?.taxonomy?.family }
                    .distinct().size
            is BadgeRule.CountDistinctOrders ->
                observations.mapNotNull { it.speciesId }
                    .mapNotNull { speciesByQid[SpeciesId(it)]?.taxonomy?.iocOrder }
                    .distinct().size
            is BadgeRule.ObservedRedListed ->
                observations.mapNotNull { it.speciesId }.distinct().count { qid ->
                    speciesByQid[SpeciesId(qid)]?.iucnStatus in RED_LISTED
                }
            is BadgeRule.ObservedBeforeHour ->
                observations.count { o ->
                    o.capturedAt.toLocalDateTime(zone).hour < rule.hour
                }
            is BadgeRule.ObservedInHourRange ->
                observations.count { o ->
                    val h = o.capturedAt.toLocalDateTime(zone).hour
                    h >= rule.startHour && h < rule.endHourExclusive
                }
            is BadgeRule.SundayStreak ->
                consecutiveSundaysWithObservations(observations.map { it.capturedAt }, zone)
            is BadgeRule.DailyBirdMatches -> dailyBirdMatchCount
            is BadgeRule.SpeciesAcrossSeasons ->
                observations
                    .mapNotNull { obs -> obs.speciesId?.let { it to obs } }
                    .groupBy({ it.first }, { it.second })
                    .values
                    .count { perSpecies ->
                        perSpecies.map { seasonOf(it.capturedAt, zone) }.toSet().size >= rule.seasons
                    }
            is BadgeRule.AudioObservationCount ->
                observations.count { it.sourceType == ObservationSource.Audio }
            is BadgeRule.ObservationsWithNote ->
                observations.count { it.note.length >= rule.minLength }
            is BadgeRule.ObservedInAllSeasons ->
                if (observations.map { seasonOf(it.capturedAt, zone) }.toSet().size >= 4) 1 else 0
            BadgeRule.Manual -> 0
        }

}
