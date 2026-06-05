package se.birdy.app.badges

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrophyProgressTest {
    private fun item(
        id: String,
        current: Int,
        target: Int,
        unlocked: Boolean = false,
    ) = BadgeProgressItem(id, current, target, unlocked)

    @Test
    fun `counts unlocked and total`() {
        val s = TrophyProgress.summarize(listOf(item("a", 5, 5, true), item("b", 2, 10), item("c", 0, 3)))
        assertEquals(1, s.unlockedCount)
        assertEquals(3, s.totalCount)
    }

    @Test
    fun `closest is the locked in-progress badge with smallest remaining`() {
        val s = TrophyProgress.summarize(listOf(item("b", 9, 10), item("e", 1, 5), item("a", 5, 5, true)))
        assertEquals("b", s.closest?.badgeId) // remaining 1 < 4
    }

    @Test
    fun `closest ignores unlocked and untouched badges`() {
        val s = TrophyProgress.summarize(listOf(item("a", 5, 5, true), item("c", 0, 3), item("f", 2, 4)))
        assertEquals("f", s.closest?.badgeId)
    }

    @Test
    fun `closest is null when nothing is in progress`() {
        val s = TrophyProgress.summarize(listOf(item("a", 5, 5, true), item("c", 0, 3)))
        assertNull(s.closest)
    }
}
