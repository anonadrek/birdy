package se.birdy.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.ui.badges.BadgeGridState
import se.birdy.app.ui.badges.BadgeWithUnlock
import se.birdy.app.ui.badges.BadgesScreen
import se.birdy.app.ui.badges.BadgesUiState
import se.birdy.app.ui.badges.LockedBadgeProgress
import se.birdy.app.ui.badges.PremiumBadgeProgress
import se.birdy.app.ui.badges.SpeciesProgress
import se.birdy.app.ui.badges.TrophyShowcase
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule

/**
 * Märken with the Troférum entry card, release 1.3.0 Task 7k (design "Bild 2", chosen by Albin
 * 2026-10-07): your three latest stamps fanned out on paper instead of the stock photo. The
 * design's own stamps: №17 Rödlistad, №2 Skådare and №23 Månads-rytm (the newest, 11 okt), out of
 * 8. The checks with assertions are in TrophyRoomEntryCardTest; this suite only renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class BadgesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // A fixed clock and zone: BadgesScreen reads dates against "now" (the year shows once it differs).
    private val zone = TimeZone.of("Europe/Stockholm")
    private val now = Instant.parse("2026-10-12T10:00:00Z")

    // The catalog's free badges in stamp order (№1 to №27), then the seven Premium ones.
    private val regular =
        listOf(
            "novice" to BadgeCategory.PROGRESSION,
            "birder_bronze" to BadgeCategory.PROGRESSION,
            "birder_silver" to BadgeCategory.PROGRESSION,
            "birder_gold" to BadgeCategory.PROGRESSION,
            "birder_legend" to BadgeCategory.PROGRESSION,
            "daily_bird_first" to BadgeCategory.PROGRESSION,
            "family_anatidae" to BadgeCategory.FAMILY,
            "family_scolopacidae" to BadgeCategory.FAMILY,
            "family_accipitridae" to BadgeCategory.FAMILY,
            "family_fringillidae" to BadgeCategory.FAMILY,
            "family_paridae" to BadgeCategory.FAMILY,
            "family_strigidae" to BadgeCategory.FAMILY,
            "family_songbirds" to BadgeCategory.FAMILY,
            "breadth_families_20" to BadgeCategory.BREADTH,
            "breadth_families_50" to BadgeCategory.BREADTH,
            "breadth_orders_20" to BadgeCategory.BREADTH,
            "redlisted_1" to BadgeCategory.REDLISTED,
            "redlisted_5" to BadgeCategory.REDLISTED,
            "redlisted_15" to BadgeCategory.REDLISTED,
            "season_all_year" to BadgeCategory.SEASON,
            "season_faithful" to BadgeCategory.SEASON,
            "audio_scholar" to BadgeCategory.AUDIO,
            "weekly_streak_4" to BadgeCategory.STREAK_WEEKLY,
            "weekly_streak_12" to BadgeCategory.STREAK_WEEKLY,
            "weekly_streak_52" to BadgeCategory.STREAK_WEEKLY,
            "monthly_streak_3" to BadgeCategory.STREAK_MONTHLY,
            "monthly_streak_12" to BadgeCategory.STREAK_MONTHLY,
        ).map { (id, category) -> Badge(id = id, category = category, rule = BadgeRule.CountUniqueSpecies(5)) }
    private val premium =
        listOf(
            "premium_field_member",
            "premium_dawn_chorus",
            "premium_early_pilgrim",
            "premium_field_journalist",
            "premium_winter_wanderer",
            "premium_sunday_birder",
            "premium_daily_bird_hunter",
        ).map { Badge(id = it, category = BadgeCategory.PROGRESSION, rule = BadgeRule.CountUniqueSpecies(1), isPremium = true) }

    private fun stampNumber(id: String) = (regular + premium).indexOfFirst { it.id == id } + 1

    // Eight stamps; the three newest are the design's №17, №2 and №23, oldest to newest.
    private val earned =
        listOf(
            "weekly_streak_4" to "2026-10-11T08:00:00Z",
            "birder_bronze" to "2026-09-28T08:00:00Z",
            "redlisted_1" to "2026-09-12T08:00:00Z",
            "season_faithful" to "2026-08-22T08:00:00Z",
            "audio_scholar" to "2026-08-03T08:00:00Z",
            "family_paridae" to "2026-07-14T08:00:00Z",
            "daily_bird_first" to "2026-06-20T08:00:00Z",
            "novice" to "2026-06-02T08:00:00Z",
        ).map { (id, at) -> BadgeWithUnlock(regular.first { it.id == id }, Instant.parse(at), stampNumber(id)) }

    private fun state(unlocked: List<BadgeWithUnlock>): BadgesUiState.Loaded {
        val unlockedIds = unlocked.map { it.badge.id }.toSet()
        val inProgress = mapOf("birder_silver" to 31, "family_anatidae" to 9, "weekly_streak_12" to 6)
        val locked =
            regular.filter { it.id !in unlockedIds }.map { badge ->
                LockedBadgeProgress(
                    badge = badge,
                    state = inProgress[badge.id]?.let { BadgeGridState.InProgress(it, badge.rule.target * 20) } ?: BadgeGridState.Locked,
                    stampNumber = stampNumber(badge.id),
                )
            }
        val recent = unlocked.take(5)
        return BadgesUiState.Loaded(
            speciesProgress = SpeciesProgress(seen = 27, total = 839),
            unlockedCount = unlocked.size,
            totalBadges = regular.size + premium.size,
            weeklyStreak = null,
            monthlyStreak = null,
            recentlyUnlocked = recent,
            locked = locked,
            premiumBadges = premium.map { PremiumBadgeProgress(it, null, BadgeGridState.Locked, stampNumber(it.id)) },
            premiumActive = false,
            trophyShowcase = TrophyShowcase(recent.firstOrNull(), recent.drop(1), emptyList(), emptyList()),
        )
    }

    private fun capture(
        name: String,
        unlocked: List<BadgeWithUnlock>,
        locale: Locale,
    ) = compose.captureScreen(name) {
        BadgesScreen(
            state = state(unlocked),
            locale = locale,
            zone = zone,
            onBadgeClick = { _, _ -> },
            onRetry = {},
            onSettingsClick = {},
            onPremiumClick = {},
            onOpenTrophyRoom = {},
            now = now,
        )
    }

    @Test
    @Config(qualifiers = "+sv")
    fun badges_sv() {
        capture("badges_sv", earned, Locale.SV)
        compose.onNodeWithText("Senast: Månads-rytm, 11 okt", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun badges_en() {
        capture("badges_en", earned, Locale.EN)
        compose.onNodeWithText("Latest: Monthly rhythm, Oct 11", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun badges_empty_sv() {
        capture("badges_empty_sv", emptyList(), Locale.SV)
        compose.onNodeWithText("Din första stämpel väntar.", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun badges_empty_en() {
        capture("badges_empty_en", emptyList(), Locale.EN)
        compose.onNodeWithText("Your first stamp awaits.", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun badges_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        capture("badges_sv_200", earned, Locale.SV)
    }

    @Test
    @Config(qualifiers = "+en")
    fun badges_en_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        capture("badges_en_200", earned, Locale.EN)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun badges_empty_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        capture("badges_empty_sv_200", emptyList(), Locale.SV)
    }

    // A narrow phone at 2.0×: the text no longer fits beside the seals, so they move below it.
    @Test
    @Config(qualifiers = "+sv-w360dp")
    fun badges_w360_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        capture("badges_w360_sv_200", earned, Locale.SV)
    }
}
