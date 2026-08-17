package se.birdy.app.notifications

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationTimesTest {
    private val zone = TimeZone.of("Europe/Stockholm")

    private fun at(
        y: Int,
        mo: Int,
        d: Int,
        h: Int,
        mi: Int,
    ) = LocalDateTime(y, mo, d, h, mi).toInstant(zone)

    @Test
    fun nextDaily_before_target_is_today() =
        assertEquals(LocalDateTime(2026, 8, 17, 8, 0), NotificationTimes.nextDaily(at(2026, 8, 17, 6, 30), zone, 8, 0))

    @Test
    fun nextDaily_after_target_rolls_to_tomorrow() =
        assertEquals(LocalDateTime(2026, 8, 18, 8, 0), NotificationTimes.nextDaily(at(2026, 8, 17, 8, 0), zone, 8, 0))

    @Test
    fun nextWeekly_same_day_before_time_is_today() =
        // 2026-08-16 är en söndag
        assertEquals(LocalDateTime(2026, 8, 16, 18, 0), NotificationTimes.nextWeekly(at(2026, 8, 16, 12, 0), zone, DayOfWeek.SUNDAY, 18, 0))

    @Test
    fun nextWeekly_same_day_at_time_rolls_a_week() =
        assertEquals(LocalDateTime(2026, 8, 23, 18, 0), NotificationTimes.nextWeekly(at(2026, 8, 16, 18, 0), zone, DayOfWeek.SUNDAY, 18, 0))

    @Test
    fun nextWeekly_other_day() =
        // onsdag 09:00 sett från söndag
        assertEquals(
            LocalDateTime(2026, 8, 19, 9, 0),
            NotificationTimes.nextWeekly(at(2026, 8, 16, 12, 0), zone, DayOfWeek.WEDNESDAY, 9, 0),
        )

    @Test
    fun upcomingDaily_returns_consecutive_days() {
        val list = NotificationTimes.upcomingDaily(at(2026, 8, 17, 9, 0), zone, 8, 0, count = 3)
        assertEquals(
            listOf(LocalDateTime(2026, 8, 18, 8, 0), LocalDateTime(2026, 8, 19, 8, 0), LocalDateTime(2026, 8, 20, 8, 0)),
            list,
        )
    }

    @Test
    fun millisUntil_is_positive_delta() =
        assertEquals(90 * 60 * 1000L, NotificationTimes.millisUntil(LocalDateTime(2026, 8, 17, 8, 0), at(2026, 8, 17, 6, 30), zone))
}
