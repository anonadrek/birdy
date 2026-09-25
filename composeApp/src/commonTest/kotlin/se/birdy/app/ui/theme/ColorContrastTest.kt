package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** WCAG 2.1 AA: normal text needs ≥ 4.5:1 against its background. Spec 2026-09-24 §4.1. */
class ColorContrastTest {
    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private val papers =
        mapOf(
            "PaperTop" to PaperTop,
            "PaperBottom" to PaperBottom,
            "MossCreme" to MossCreme,
            "CardPaper" to CardPaper,
            "SandCreme" to SandCreme,
        )

    @Test
    fun `text tokens reach AA on every paper surface`() {
        val texts =
            mapOf(
                "TextOnCreme" to TextOnCreme,
                "MarginaliaInk" to MarginaliaInk,
                "InkMuted" to InkMuted,
                "AccentCopper" to AccentCopper,
                "StampNavy" to StampNavy,
            )
        val failures =
            texts.flatMap { (tn, t) ->
                papers.mapNotNull { (pn, p) -> contrast(t, p).takeIf { it < 4.5 }?.let { "$tn on $pn = $it" } }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `light tokens reach AA on dark moss`() {
        val light = mapOf("TextOnHero" to TextOnHero, "AccentCopperLight" to AccentCopperLight, "BrassLight" to BrassLight)
        val failures = light.mapNotNull { (n, c) -> contrast(c, HeroMossDeep).takeIf { it < 4.5 }?.let { "$n on HeroMossDeep = $it" } }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `button text reaches AA on rust and brass fills`() {
        assertTrue(contrast(TextOnHero, AccentCopper) >= 4.5)
        assertTrue(contrast(BrassInk, Brass) >= 4.5)
    }
}
