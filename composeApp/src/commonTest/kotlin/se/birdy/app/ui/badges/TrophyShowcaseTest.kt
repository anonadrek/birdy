package se.birdy.app.ui.badges

import kotlinx.datetime.Instant
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrophyShowcaseTest {
    private fun bwu(
        id: String,
        ms: Long,
        category: BadgeCategory = BadgeCategory.PROGRESSION,
        stamp: Int = 0,
    ) = BadgeWithUnlock(
        badge = Badge(id = id, category = category, rule = BadgeRule.CountUniqueSpecies(1)),
        unlockedAt = Instant.fromEpochMilliseconds(ms),
        stampNumber = stamp,
    )

    private fun lbp(
        id: String,
        current: Int?,
        target: Int,
    ) = LockedBadgeProgress(
        badge = Badge(id = id, category = BadgeCategory.PROGRESSION, rule = BadgeRule.CountUniqueSpecies(target)),
        state = if (current != null) BadgeGridState.InProgress(current, target) else BadgeGridState.Locked,
    )

    @Test
    fun `hero is first recently unlocked and recent band drops it`() {
        val recent = listOf(bwu("a", 5000), bwu("b", 4000), bwu("c", 3000))
        val s = buildTrophyShowcase(recentlyUnlocked = recent, allUnlocked = recent, locked = emptyList())
        assertEquals("a", s.hero?.badge?.id)
        assertEquals(listOf("b", "c"), s.recentlyUnlocked.map { it.badge.id })
    }

    @Test
    fun `empty recently unlocked yields null hero and empty bands`() {
        val s = buildTrophyShowcase(emptyList(), emptyList(), emptyList())
        assertNull(s.hero)
        assertTrue(s.recentlyUnlocked.isEmpty())
        assertTrue(s.rareFinds.isEmpty())
        assertTrue(s.closeToUnlock.isEmpty())
    }

    @Test
    fun `rare finds keep only REDLISTED sorted by unlocked desc`() {
        val all =
            listOf(
                bwu("prog", 9000, BadgeCategory.PROGRESSION),
                bwu("red_old", 1000, BadgeCategory.REDLISTED),
                bwu("red_new", 8000, BadgeCategory.REDLISTED),
                bwu("fam", 7000, BadgeCategory.FAMILY),
            )
        val s = buildTrophyShowcase(recentlyUnlocked = all.take(5), allUnlocked = all, locked = emptyList())
        assertEquals(listOf("red_new", "red_old"), s.rareFinds.map { it.badge.id })
    }

    @Test
    fun `close to unlock sorts by ratio takes 3 excludes locked and zero-target`() {
        val locked =
            listOf(
                lbp("almost", current = 4, target = 5), // 0.80
                lbp("half", current = 5, target = 10), // 0.50
                lbp("barely", current = 1, target = 100), // 0.01
                lbp("locked", current = null, target = 5), // exkluderas (Locked)
                lbp("zero", current = 0, target = 0), // exkluderas (target 0)
                lbp("nearest", current = 18, target = 20), // 0.90
            )
        val s = buildTrophyShowcase(emptyList(), emptyList(), locked)
        assertEquals(listOf("nearest", "almost", "half"), s.closeToUnlock.map { it.badge.id })
    }
}
