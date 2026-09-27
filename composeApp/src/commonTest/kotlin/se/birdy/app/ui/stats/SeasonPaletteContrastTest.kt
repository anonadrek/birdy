package se.birdy.app.ui.stats

import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.contrastRatio
import se.birdy.app.ui.theme.relativeLuminance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T12b review fix: pins two things about [SeasonPalette] so the summer/autumn regression
 * (Brass swapped for BrassText, indistinguishable for deuteranopes) can't silently recur.
 *
 * 1. Every season swatch clears WCAG 1.4.11's 3:1 graphics-object floor against [CardPaper] —
 *    the only surface [se.birdy.app.ui.stats.charts.JournalDonutChart] and the legend ever draw
 *    on ([se.birdy.app.ui.components.SectionCard] always sits on `CardPaper`). Brass is the
 *    tightest of the four at ≈3.02:1 — honestly close to the floor, not padded, but real.
 * 2. Summer and autumn are apart by luminance, not just by hue — a rough proxy for "still
 *    distinguishable once desaturated" (a full CVD (color-vision-deficiency) simulation needs
 *    more machinery than is available in a commonTest; luminance separation is the cheap,
 *    honest stand-in the review itself used to explain the regression).
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
        val summerLuminance = relativeLuminance(SeasonPalette.summer)
        val autumnLuminance = relativeLuminance(SeasonPalette.autumn)
        val gap = kotlin.math.abs(summerLuminance - autumnLuminance)
        // The BrassText regression measured ~1.1:1 luminance-ratio apart (indistinguishable).
        // Brass vs AccentCopper measures a real gap here; 0.03 absolute luminance is a
        // deliberately loose floor — this test exists to catch "swapped back to something
        // BrassText-close", not to pin the exact current values.
        assertTrue(
            gap > MIN_SEASON_LUMINANCE_GAP,
            "summer ($summerLuminance) and autumn ($autumnLuminance) are only $gap apart",
        )
    }

    private companion object {
        const val MIN_SEASON_LUMINANCE_GAP = 0.03
    }
}
