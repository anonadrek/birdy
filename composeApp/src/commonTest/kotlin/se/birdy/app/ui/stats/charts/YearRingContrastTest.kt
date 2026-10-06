package se.birdy.app.ui.stats.charts

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Brass
import se.birdy.app.ui.theme.BrassInk
import se.birdy.app.ui.theme.BrassText
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.PaperBottom
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7c: pins the year ring's color pairs (replaces SeasonPaletteContrastTest,
 * which guarded the season donut that the ring replaced). The ring is drawn straight on the
 * paper background, its middle on CardPaper.
 */
class YearRingContrastTest {
    private val papers = mapOf("PaperTop" to PaperTop, "PaperBottom" to PaperBottom, "MossCreme" to MossCreme)

    private fun assertAll(
        minimum: Double,
        pairs: Map<String, Pair<Color, Color>>,
    ) {
        val failures =
            pairs.mapNotNull { (name, colors) ->
                contrastRatio(colors.first, colors.second).takeIf { it < minimum }?.let { "$name = $it" }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `month segments stand out from the paper as graphics`() {
        // WCAG 1.4.11: 3:1. A rust segment on its own; the brass (current month) segment through
        // its BrassText edge, because a brass fill alone is below 3:1 on paper.
        assertAll(3.0, papers.mapKeys { "rust on ${it.key}" }.mapValues { AccentCopper to it.value })
        assertAll(3.0, papers.mapKeys { "brass edge on ${it.key}" }.mapValues { BrassText to it.value })
        assertTrue(papers.values.any { contrastRatio(Brass, it) < 3.0 }, "brass fill alone now clears 3:1: the edge may be dropped")
    }

    @Test
    fun `month counts are readable inside and outside their segment`() {
        assertAll(
            4.5,
            mapOf(
                "best month count on rust" to (TextOnHero to AccentCopper),
                "current month count on brass" to (BrassInk to Brass),
            ) +
                papers.mapKeys { "best month count beside its segment on ${it.key}" }.mapValues { AccentCopper to it.value } +
                papers.mapKeys { "current month count beside its segment on ${it.key}" }.mapValues { BrassText to it.value },
        )
    }

    @Test
    fun `letters and the center text are readable`() {
        assertAll(
            4.5,
            papers.mapKeys { "month letter on ${it.key}" }.mapValues { InkMuted to it.value } +
                papers.mapKeys { "current month letter on ${it.key}" }.mapValues { TextOnCreme to it.value } +
                mapOf(
                    "species note on CardPaper" to (AccentCopper to CardPaper),
                    "total on CardPaper" to (TextOnCreme to CardPaper),
                    "finds label on CardPaper" to (InkMuted to CardPaper),
                ),
        )
    }
}
