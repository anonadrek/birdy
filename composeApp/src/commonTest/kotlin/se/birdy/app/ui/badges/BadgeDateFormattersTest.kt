package se.birdy.app.ui.badges

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.content.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class BadgeDateFormattersTest {
    private val zone = TimeZone.UTC

    // 2026-05-06T10:00:00Z → 6 May 2026
    private val mayInstant = Instant.parse("2026-05-06T10:00:00Z")

    // 2026-10-31T23:59:00Z → 31 Oct 2026 (catches month-index off-by-one + Swedish "okt")
    private val octInstant = Instant.parse("2026-10-31T23:59:00Z")

    @Test
    fun `short date SV uses lowercase swedish month with day-first`() {
        assertEquals("6 maj", formatBadgeShortDate(mayInstant, zone, Locale.SV))
        assertEquals("31 okt", formatBadgeShortDate(octInstant, zone, Locale.SV))
    }

    @Test
    fun `short date EN uses capitalized english month with month-first`() {
        assertEquals("May 6", formatBadgeShortDate(mayInstant, zone, Locale.EN))
        assertEquals("Oct 31", formatBadgeShortDate(octInstant, zone, Locale.EN))
    }

    @Test
    fun `full date SV is day month year without comma`() {
        assertEquals("6 maj 2026", formatBadgeFullDate(mayInstant, zone, Locale.SV))
        assertEquals("31 okt 2026", formatBadgeFullDate(octInstant, zone, Locale.SV))
    }

    @Test
    fun `full date EN is month day comma year`() {
        assertEquals("May 6, 2026", formatBadgeFullDate(mayInstant, zone, Locale.EN))
        assertEquals("Oct 31, 2026", formatBadgeFullDate(octInstant, zone, Locale.EN))
    }
}
