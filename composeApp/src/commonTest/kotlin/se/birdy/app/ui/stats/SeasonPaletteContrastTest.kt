package se.birdy.app.ui.stats

import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T12b/T12c review fixes: pins two things about [SeasonPalette] so the summer/autumn regression
 * (Brass swapped for BrassText, indistinguishable for deuteranopes) can't silently recur.
 *
 * 1. Every season swatch clears WCAG 1.4.11's 3:1 graphics-object floor against [CardPaper] —
 *    the only surface [se.birdy.app.ui.stats.charts.JournalDonutChart] and the legend ever draw
 *    on ([se.birdy.app.ui.components.SectionCard] always sits on `CardPaper`). Brass is the
 *    tightest of the four at ≈3.02:1 — honestly close to the floor, not padded, but real.
 * 2. Summer and autumn are apart by luminance-contrast ratio, not just by hue — a rough proxy for
 *    "still distinguishable once desaturated" (a full CVD (color-vision-deficiency) simulation
 *    needs more machinery than is available in a commonTest; contrast ratio is the cheap, honest
 *    stand-in the review itself used to explain the regression). T12c: switched from an absolute
 *    luminance-gap floor (0.03, opaque and not directly comparable to the KDoc's own ":1" ratios)
 *    to the same `contrastRatio` used everywhere else in this file — 1.8:1 sits between the
 *    BrassText regression (1.10:1, must fail) and today's real Brass/AccentCopper gap (2.05:1,
 *    must pass).
 */
class SeasonPaletteContrastTest {
    @Test
    fun `every season swatch clears 3 to 1 on CardPaper`() {
        val cases =
            mapOf(
                "winter" to SeasonPalette.winter,
                "spring" to SeasonPalette.spring,
                "summer" to SeasonPalette.summer,
                "autumn" to SeasonPalette.autumn,
            )
        val ratios = cases.mapValues { (_, color) -> contrastRatio(color, CardPaper) }
        val failures = ratios.filterValues { it < 3.0 }
        assertTrue(
            failures.isEmpty(),
            "Below 3:1 on CardPaper: $failures (all ratios: $ratios)",
        )
    }

    @Test
    fun `summer and autumn are apart by luminance`() {
        val ratio = contrastRatio(SeasonPalette.summer, SeasonPalette.autumn)
        // The BrassText regression measured ~1.10:1 luminance-ratio apart (indistinguishable).
        // Brass vs AccentCopper measures ≈2.05:1 — a real gap. The floor sits between the two,
        // closer to the regression value than to today's real one, so it stays loose enough not
        // to pin the exact current colors while still catching "swapped back to something
        // BrassText-close".
        assertTrue(
            ratio >= MIN_SEASON_LUMINANCE_RATIO,
            "summer/autumn contrast ratio is only $ratio:1",
        )
    }

    private companion object {
        const val MIN_SEASON_LUMINANCE_RATIO = 1.8
    }
}
