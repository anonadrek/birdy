package se.birdy.pdf

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class JournalPdfMetricsTest {
    private val zone = TimeZone.of("Europe/Stockholm")

    // 2026-05-20T13:20:00Z = 15:20 svensk sommartid (CEST)
    private val epochMs = 1779283200000L

    @Test
    fun formatDate_pads_month_and_day() = assertEquals("2026-05-20", JournalPdfMetrics.formatDate(epochMs, zone))

    @Test
    fun formatDateTime_includes_hour_minute() = assertEquals("2026-05-20 15:20", JournalPdfMetrics.formatDateTime(epochMs, zone))

    @Test
    fun yearOf_resolves_in_zone() = assertEquals(2026, JournalPdfMetrics.yearOf(epochMs, zone))

    @Test
    fun fmt_replaces_placeholders_in_order() =
        assertEquals("3 arter sedda • 7 fynd", JournalPdfMetrics.fmt(JournalPdfMetrics.TEASER_FMT, "3", "7"))
}
