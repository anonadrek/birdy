package se.birdy.domain.badge

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BadgeProgressTest {
    private val sampleBadge =
        Badge(
            id = "novice",
            category = BadgeCategory.PROGRESSION,
            rule = BadgeRule.CountUniqueSpecies(target = 5),
        )

    @Test
    fun `isUnlocked false when unlock is null`() {
        val p = BadgeProgress(sampleBadge, current = 2, target = 5, unlock = null)
        assertFalse(p.isUnlocked)
    }

    @Test
    fun `isUnlocked true when unlock is set`() {
        val p =
            BadgeProgress(
                sampleBadge,
                current = 5,
                target = 5,
                unlock = BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_000_000)),
            )
        assertTrue(p.isUnlocked)
    }

    @Test
    fun `progressFraction half when current is half of target`() {
        val p = BadgeProgress(sampleBadge, current = 2, target = 4, unlock = null)
        assertEquals(0.5f, p.progressFraction)
    }

    @Test
    fun `progressFraction caps at 1 when current exceeds target`() {
        val p = BadgeProgress(sampleBadge, current = 10, target = 5, unlock = null)
        assertEquals(1f, p.progressFraction)
    }
}
