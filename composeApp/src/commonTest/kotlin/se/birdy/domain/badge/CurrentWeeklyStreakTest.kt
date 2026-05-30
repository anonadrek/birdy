package se.birdy.domain.badge

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class CurrentWeeklyStreakTest {
    private val utc = TimeZone.UTC

    // 2026-05-25 är en måndag (ISO-vecka 22). Veckor bakåt: v21 mån = 2026-05-18, v20 = 2026-05-11.
    private fun monday(week: Int): Instant {
        val base = Instant.parse("2026-05-25T10:00:00Z") // v22 mån
        val weeksBack = 22 - week
        return base.minus((weeksBack * 7).days)
    }

    private val now = Instant.parse("2026-05-27T10:00:00Z") // onsdag v22

    @Test
    fun `empty list yields zero`() {
        assertEquals(0, currentWeeklyStreak(emptyList(), utc, now))
    }

    @Test
    fun `obs this week and two prior weeks yields three`() {
        val obs = listOf(monday(22), monday(21), monday(20))
        assertEquals(3, currentWeeklyStreak(obs, utc, now))
    }

    @Test
    fun `gap breaks the streak`() {
        val obs = listOf(monday(22), monday(20)) // v21 saknas
        assertEquals(1, currentWeeklyStreak(obs, utc, now))
    }

    @Test
    fun `quiet current week but last two weeks active keeps streak alive`() {
        val obs = listOf(monday(21), monday(20)) // inget i v22
        assertEquals(2, currentWeeklyStreak(obs, utc, now))
    }

    @Test
    fun `no obs current or last week yields zero`() {
        val obs = listOf(monday(20), monday(19))
        assertEquals(0, currentWeeklyStreak(obs, utc, now))
    }

    @Test
    fun `prev crosses year boundary`() {
        val w1 = WeekKey(2026, 1)
        assertEquals(WeekKey(2025, isoWeeksInYear(2025)), w1.prev())
    }
}
