package se.birdy.app.badges

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.domain.badge.BadgeAbundance
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.consecutiveSundaysWithObservations
import se.birdy.domain.badge.longestMonthlyStreak
import se.birdy.domain.badge.longestWeeklyStreak
import se.birdy.domain.badge.seasonOf
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationSource
import se.birdy.content.Abundance as ContentAbundance

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
                observations.count { o ->
                    val qid = o.speciesId ?: return@count false
                    speciesByQid[SpeciesId(qid)]?.taxonomy?.family == rule.family
                }
            is BadgeRule.ObservedWithAbundance ->
                observations.count { o ->
                    val qid = o.speciesId ?: return@count false
                    speciesByQid[SpeciesId(qid)]?.abundance?.let(::mapAbundance) == rule.abundance
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

    private fun mapAbundance(content: ContentAbundance): BadgeAbundance =
        when (content) {
            ContentAbundance.ALLMÄN -> BadgeAbundance.ALLMÄN
            ContentAbundance.MINDRE_ALLMÄN -> BadgeAbundance.MINDRE_ALLMÄN
            ContentAbundance.OVANLIG -> BadgeAbundance.OVANLIG
            ContentAbundance.SÄLLSYNT -> BadgeAbundance.SÄLLSYNT
        }
}
