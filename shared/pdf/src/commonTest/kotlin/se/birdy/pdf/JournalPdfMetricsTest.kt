package se.birdy.pdf

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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

    // Release 1.3.1 part 7 (Albin 2026-10-08): no dashes in what a user reads, the page number stands alone.
    @Test
    fun footer_is_the_page_number_without_dashes() {
        assertEquals("3", JournalPdfMetrics.fmt(JournalPdfMetrics.FOOTER_FMT, "3"))
        assertFalse(Regex("[\u2013\u2014]").containsMatchIn(JournalPdfMetrics.FOOTER_FMT))
    }

    // Part 8: a species name runs from after the thumbnail to the count column, minus the gap.
    @Test
    fun species_text_width_ends_a_gap_before_the_count_column() =
        assertEquals(595f - 2 * 50f - 26f - 120f - 8f, JournalPdfMetrics.SPECIES_TEXT_MAX_W)

    // Part 8: a badge's name and description run from after the stamp to the date column, minus the gap.
    @Test
    fun badge_text_width_ends_a_gap_before_the_date_column() =
        assertEquals(595f - 2 * 50f - 44f - 90f - 8f, JournalPdfMetrics.BADGE_TEXT_MAX_W)

    // teaser() moved to JournalPdfStrings when the PDF's text became per-locale (an English
    // user's export was printing Swedish headings) — see JournalPdfStringsTest for its coverage,
    // including the Task 7g singular/plural rule this test used to cover here.
}
