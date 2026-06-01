package se.birdy.app.ui.badges

import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BadgeLadderTest {
    private fun badge(
        id: String,
        rule: BadgeRule,
        category: BadgeCategory = BadgeCategory.PROGRESSION,
    ) = Badge(id = id, category = category, rule = rule)

    private fun lbp(
        b: Badge,
        current: Int? = null,
    ) = LockedBadgeProgress(
        badge = b,
        state = if (current != null) BadgeGridState.InProgress(current, b.rule.target) else BadgeGridState.Locked,
    )

    @Test
    fun `points to next un-earned tier with remaining`() {
        val unlocked = badge("birder_silver", BadgeRule.CountUniqueSpecies(100))
        val locked =
            listOf(
                lbp(badge("birder_gold", BadgeRule.CountUniqueSpecies(250)), current = 150),
                lbp(badge("birder_legend", BadgeRule.CountUniqueSpecies(500)), current = 150),
            )
        val next = BadgeLadder.nextTier(unlocked, locked)
        assertEquals("birder_gold", next?.nextBadgeId)
        assertEquals(100, next?.remaining) // 250 - 150
    }

    @Test
    fun `returns null at the top tier`() {
        val unlocked = badge("birder_legend", BadgeRule.CountUniqueSpecies(500))
        assertNull(BadgeLadder.nextTier(unlocked, emptyList()))
    }

    @Test
    fun `returns null for a non-laddered rule`() {
        val unlocked = badge("season_all_year", BadgeRule.ObservedInAllSeasons(1), BadgeCategory.SEASON)
        val locked = listOf(lbp(badge("season_faithful", BadgeRule.SpeciesAcrossSeasons(4, 1))))
        assertNull(BadgeLadder.nextTier(unlocked, locked))
    }

    @Test
    fun `families and orders ladders do not cross`() {
        val unlocked = badge("breadth_families_20", BadgeRule.CountDistinctFamilies(20), BadgeCategory.BREADTH)
        val locked =
            listOf(
                lbp(badge("breadth_families_50", BadgeRule.CountDistinctFamilies(50)), current = 30),
                lbp(badge("breadth_orders_20", BadgeRule.CountDistinctOrders(20)), current = 12),
            )
        assertEquals("breadth_families_50", BadgeLadder.nextTier(unlocked, locked)?.nextBadgeId)
    }
}
