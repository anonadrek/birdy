package se.birdy.app.ui.diary

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RelativeDateTest {
    private val tz = TimeZone.of("Europe/Stockholm")
    private val now = Instant.parse("2026-05-06T10:00:00Z") // 12:00 Stockholm

    @Test
    fun today_is_recognized() {
        val ts = Instant.parse("2026-05-06T07:14:00Z") // 09:14 Stockholm — same day
        val result = relativeDate(ts, now, tz)
        val today = assertIs<RelativeDateText.Today>(result)
        assertEquals("09:14", today.timeOfDay)
    }

    @Test
    fun yesterday_is_recognized() {
        val ts = Instant.parse("2026-05-05T15:22:00Z") // 17:22 Stockholm — yesterday
        val result = relativeDate(ts, now, tz)
        val yesterday = assertIs<RelativeDateText.Yesterday>(result)
        assertEquals("17:22", yesterday.timeOfDay)
    }

    @Test
    fun within_seven_days_is_relative() {
        val ts = Instant.parse("2026-05-03T09:08:00Z") // 11:08 Stockholm — 3 days ago
        val result = relativeDate(ts, now, tz)
        val rel = assertIs<RelativeDateText.WithinWeek>(result)
        assertEquals(3, rel.dayOfMonth)
        assertEquals(5, rel.month1Based)
        assertEquals("11:08", rel.timeOfDay)
    }

    @Test
    fun older_than_seven_days_is_full() {
        val ts = Instant.parse("2026-04-29T06:55:00Z") // 08:55 Stockholm — 7 days ago
        val result = relativeDate(ts, now, tz)
        val full = assertIs<RelativeDateText.WithinWeek>(result) // 7 days = still relative
        assertEquals(29, full.dayOfMonth)
        assertEquals(4, full.month1Based)
    }

    @Test
    fun very_old_is_full() {
        val ts = Instant.parse("2025-10-15T10:00:00Z")
        val result = relativeDate(ts, now, tz)
        val full = assertIs<RelativeDateText.Full>(result)
        assertEquals(2025, full.year)
        assertEquals(15, full.dayOfMonth)
        assertEquals(10, full.month1Based)
        assertEquals("12:00", full.timeOfDay)
    }
}
