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
    fun fmt_replaces_placeholders_in_order() = assertEquals("3 av 7", JournalPdfMetrics.fmt("%s av %s", "3", "7"))

    // teaser() moved to JournalPdfStrings when the PDF's text became per-locale (an English
    // user's export was printing Swedish headings) — see JournalPdfStringsTest for its coverage,
    // including the Task 7g singular/plural rule this test used to cover here.
}
