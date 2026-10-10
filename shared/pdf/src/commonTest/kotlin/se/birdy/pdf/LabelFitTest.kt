package se.birdy.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The PDF's fixed label columns (the top-species chart's 140 pt before the bars) are filled the same way on Android and
 * iOS by [fitLabel]: whole text at its size, else whole text shrunk to [JournalPdfMetrics.LABEL_MIN_SCALE] at most, else
 * cut with an ellipsis. Found 2026-10-08 on the emulator: the English export's "Great Spotted Woodpecker" ran into its
 * bar. The fake font here is 10 pt per character, so widths are easy to read.
 */
class LabelFitTest {
    private val tenPerChar: (String) -> Float = { it.length * 10f }

    @Test
    fun a_label_that_fits_is_drawn_whole_at_its_own_size() {
        assertEquals(FittedLabel("Talgoxe", 1f), fitLabel("Talgoxe", 132f, 0.75f, tenPerChar))
        assertEquals(FittedLabel("", 1f), fitLabel("", 132f, 0.75f, tenPerChar))
    }

    @Test
    fun a_label_that_fits_exactly_is_not_shrunk() {
        assertEquals(FittedLabel("1234567890", 1f), fitLabel("1234567890", 100f, 0.75f, tenPerChar))
    }

    @Test
    fun a_somewhat_long_label_is_shrunk_whole_to_the_column() {
        // 24 characters = 240 pt into 200 pt: scale 200/240, never below the minimum, nothing cut.
        val fitted = fitLabel("Great Spotted Woodpecker", 200f, 0.75f, tenPerChar)
        assertEquals("Great Spotted Woodpecker", fitted.text)
        assertEquals(200f / 240f, fitted.scale, 0.0001f)
        assertTrue(fitted.scale >= 0.75f)
    }

    @Test
    fun a_label_too_long_even_at_the_minimum_scale_is_cut_with_an_ellipsis() {
        // 30 characters = 300 pt; at 0.75 that is 225 pt > 200 pt, so the label is cut to what fits in 200 / 0.75 =
        // 266.7 pt at scale 1: 25 characters plus the ellipsis (26 × 10 = 260 pt).
        val text = "abcdefghijklmnopqrstuvwxyzABCD"
        val fitted = fitLabel(text, 200f, 0.75f, tenPerChar)
        assertEquals(0.75f, fitted.scale)
        assertEquals(text.take(25) + "…", fitted.text)
        assertTrue(tenPerChar(fitted.text) * fitted.scale <= 200f)
    }

    @Test
    fun the_cut_never_leaves_a_space_before_the_ellipsis() {
        // 290 pt is too wide even at 0.75 (217.5 > 140), so the text is cut to what fits in 140 / 0.75 = 186.7 pt:
        // "Kungsfiskare och a…" would be 190 pt, "Kungsfiskare och …" ends in a space, which is trimmed.
        val fitted = fitLabel("Kungsfiskare och andra fåglar", 140f, 0.75f, tenPerChar)
        assertEquals(FittedLabel("Kungsfiskare och…", 0.75f), fitted)
        assertTrue(tenPerChar(fitted.text) * fitted.scale <= 140f)
    }

    @Test
    fun a_column_too_narrow_for_any_letter_gets_just_the_ellipsis() {
        assertEquals(FittedLabel("…", 0.75f), fitLabel("Talgoxe", 5f, 0.75f, tenPerChar))
    }

    @Test
    fun the_chart_column_and_minimum_scale_are_shared_metrics() {
        assertEquals(140f, JournalPdfMetrics.TOPS_BAR_OFFSET)
        assertTrue(JournalPdfMetrics.TOPS_LABEL_GAP > 0f && JournalPdfMetrics.TOPS_LABEL_GAP < JournalPdfMetrics.TOPS_BAR_OFFSET)
        assertTrue(JournalPdfMetrics.LABEL_MIN_SCALE in 0.5f..1f)
    }
}
