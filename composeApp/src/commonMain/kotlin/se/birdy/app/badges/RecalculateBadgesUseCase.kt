package se.birdy.app.badges

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.domain.badge.BadgeAbundance
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.longestMonthlyStreak
import se.birdy.domain.badge.longestWeeklyStreak
import se.birdy.domain.badge.seasonOf
import se.birdy.domain.observation.Observation
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
    ): List<BadgeUnlock> {
        val now = clock.now()
        return catalog.badges
            .filter { it.id !in existingUnlocks }
            .filter { evaluate(it.rule, observations, speciesByQid) }
            .map { BadgeUnlock(it.id, now) }
    }

    fun currentValue(
        rule: BadgeRule,
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
    ): Int = rawValue(rule, observations, speciesByQid).coerceAtMost(rule.target)

    private fun evaluate(
        rule: BadgeRule,
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
    ): Boolean = rawValue(rule, observations, speciesByQid) >= rule.target

    private fun rawValue(
        rule: BadgeRule,
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
    ): Int =
        when (rule) {
            is BadgeRule.CountUniqueSpecies -> observations.map { it.speciesId }.toSet().size
            is BadgeRule.WeeklyStreak -> longestWeeklyStreak(observations.map { it.capturedAt }, zone)
            is BadgeRule.MonthlyStreak -> longestMonthlyStreak(observations.map { it.capturedAt }, zone)
            is BadgeRule.ObservedInSeason ->
                observations.count { seasonOf(it.capturedAt, zone) == rule.season }
            is BadgeRule.ObservedInFamily ->
                observations.count { speciesByQid[SpeciesId(it.speciesId)]?.taxonomy?.family == rule.family }
            is BadgeRule.ObservedWithAbundance ->
                observations.count {
                    speciesByQid[SpeciesId(it.speciesId)]?.abundance?.let(::mapAbundance) == rule.abundance
                }
        }

    private fun mapAbundance(content: ContentAbundance): BadgeAbundance =
        when (content) {
            ContentAbundance.ALLMÄN -> BadgeAbundance.ALLMÄN
            ContentAbundance.MINDRE_ALLMÄN -> BadgeAbundance.MINDRE_ALLMÄN
            ContentAbundance.OVANLIG -> BadgeAbundance.OVANLIG
            ContentAbundance.SÄLLSYNT -> BadgeAbundance.SÄLLSYNT
        }
}
