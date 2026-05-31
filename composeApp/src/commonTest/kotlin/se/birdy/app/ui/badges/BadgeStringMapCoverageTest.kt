package se.birdy.app.ui.badges

import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import kotlin.test.Test

class BadgeStringMapCoverageTest {
    private val allBadgeIds = listOf(
        // 26 free
        "novice", "birder_bronze", "birder_silver", "birder_gold", "birder_legend",
        "family_anatidae", "family_scolopacidae", "family_accipitridae", "family_fringillidae", "family_paridae", "family_strigidae", "family_songbirds",
        "breadth_families_20", "breadth_families_50", "breadth_orders_20",
        "redlisted_1", "redlisted_5", "redlisted_15",
        "season_all_year", "season_faithful", "audio_scholar",
        "weekly_streak_4", "weekly_streak_12", "weekly_streak_52", "monthly_streak_3", "monthly_streak_12",
        // 7 premium
        "premium_field_member", "premium_dawn_chorus", "premium_early_pilgrim", "premium_field_journalist", "premium_winter_wanderer", "premium_sunday_birder", "premium_daily_bird_hunter",
    )

    @Test
    fun `every catalog badge id has a name and description resource`() {
        allBadgeIds.forEach { id ->
            BadgeStringMap.nameFor(id)        // throws error(...) if missing
            BadgeStringMap.descriptionFor(id)
        }
    }

    @Test
    fun `count-based milestone badges expose a progress unit`() {
        fun badgeOf(id: String, rule: BadgeRule) =
            Badge(id = id, category = BadgeCategory.PROGRESSION, rule = rule)

        val countBased = listOf(
            badgeOf("novice", BadgeRule.CountUniqueSpecies(5)),
            badgeOf("breadth_families_20", BadgeRule.CountDistinctFamilies(20)),
            badgeOf("breadth_orders_20", BadgeRule.CountDistinctOrders(20)),
            badgeOf("redlisted_1", BadgeRule.ObservedRedListed(1)),
            badgeOf("family_anatidae", BadgeRule.ObservedInFamily("anatidae", 15)),
            badgeOf("family_songbirds", BadgeRule.ObservedInFamilyGroup(setOf("phylloscopidae"), 15)),
            badgeOf("audio_scholar", BadgeRule.AudioObservationCount(5)),
        )
        countBased.forEach { kotlin.test.assertNotNull(BadgeStringMap.unitFor(it), "saknar enhet: ${it.id}") }
    }
}
