package se.birdy.app.ui.badges

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import se.birdy.content.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatRelativeBadgeDateTest {
    private val utc = TimeZone.UTC

    @Test
    fun `sv same year returns dag mån`() {
        val now = LocalDateTime(2026, 5, 6, 12, 0).toInstant(utc)
        val target = LocalDateTime(2026, 5, 3, 12, 0).toInstant(utc)
        assertEquals("3 maj", formatRelativeBadgeDate(target, now, utc, Locale.SV))
    }

    @Test
    fun `sv different year includes year`() {
        val now = LocalDateTime(2027, 1, 5, 12, 0).toInstant(utc)
        val target = LocalDateTime(2026, 12, 30, 12, 0).toInstant(utc)
        assertEquals("30 dec 2026", formatRelativeBadgeDate(target, now, utc, Locale.SV))
    }

    @Test
    fun `en same year returns Mon Day`() {
        val now = LocalDateTime(2026, 5, 6, 12, 0).toInstant(utc)
        val target = LocalDateTime(2026, 5, 3, 12, 0).toInstant(utc)
        assertEquals("May 3", formatRelativeBadgeDate(target, now, utc, Locale.EN))
    }

    @Test
    fun `en different year appends comma year`() {
        val now = LocalDateTime(2027, 1, 5, 12, 0).toInstant(utc)
        val target = LocalDateTime(2026, 12, 30, 12, 0).toInstant(utc)
        assertEquals("Dec 30, 2026", formatRelativeBadgeDate(target, now, utc, Locale.EN))
    }

    // Month-array boundary: month 1 (Jan) and month 12 (Dec) must not off-by-one.
    @Test
    fun `sv jan boundary uses jan`() {
        val now = LocalDateTime(2026, 1, 15, 12, 0).toInstant(utc)
        val target = LocalDateTime(2026, 1, 2, 12, 0).toInstant(utc)
        assertEquals("2 jan", formatRelativeBadgeDate(target, now, utc, Locale.SV))
    }

    @Test
    fun `en dec boundary uses Dec`() {
        val now = LocalDateTime(2026, 12, 31, 23, 59).toInstant(utc)
        val target = LocalDateTime(2026, 12, 1, 0, 0).toInstant(utc)
        assertEquals("Dec 1", formatRelativeBadgeDate(target, now, utc, Locale.EN))
    }
}
