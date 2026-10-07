package se.birdy.app.notifications

import se.birdy.domain.badge.WeekKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Release 1.3.0 Task 7j review: the weekly recap's link names its week, so the Sunday notification
 * opened after midnight still shows the week it described.
 */
class BirdyDeepLinksTest {
    @Test
    fun `the recap link names its ISO week`() {
        assertEquals("birdy://recap?week=2026-W41", BirdyDeepLinks.recap(WeekKey(2026, 41)))
        assertEquals("birdy://recap?week=2027-W01", BirdyDeepLinks.recap(WeekKey(2027, 1)))
    }

    @Test
    fun `the week is read back from the link`() {
        assertEquals("2026-W41", BirdyDeepLinks.recapWeek("birdy://recap?week=2026-W41"))
        assertEquals("2026-W41", BirdyDeepLinks.recapWeek("birdy://recap?from=push&week=2026-W41"))
    }

    @Test
    fun `a link without a week has none`() {
        assertNull(BirdyDeepLinks.recapWeek("birdy://recap"))
        assertNull(BirdyDeepLinks.recapWeek("birdy://recap?week="))
        assertNull(BirdyDeepLinks.recapWeek("birdy://recap?from=push"))
    }
}
