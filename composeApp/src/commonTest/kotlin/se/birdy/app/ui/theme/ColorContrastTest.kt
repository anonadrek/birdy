package se.birdy.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 AA: normal text needs ≥ 4.5:1 against its background. Spec 2026-09-24 §4.1.
 * The luminance/contrast math itself lives in ContrastMath.kt (shared with
 * [se.birdy.app.ui.components.PhotoHeroContrastTest]).
 */
class ColorContrastTest {
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
                "BrassText" to BrassText,
                "MatchHigh" to MatchHigh,
                "MatchMid" to MatchMid,
                "MatchLow" to MatchLow,
            )
        val failures =
            texts.flatMap { (tn, t) ->
                papers.mapNotNull { (pn, p) -> contrastRatio(t, p).takeIf { it < 4.5 }?.let { "$tn on $pn = $it" } }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `OutlineInk reaches non-text AA on every paper surface`() {
        val failures =
            papers.mapNotNull { (pn, p) -> contrastRatio(OutlineInk, p).takeIf { it < 3.0 }?.let { "OutlineInk on $pn = $it" } }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `light tokens reach AA on dark moss`() {
        val light = mapOf("TextOnHero" to TextOnHero, "AccentCopperLight" to AccentCopperLight, "BrassLight" to BrassLight)
        val failures =
            light.mapNotNull { (n, c) -> contrastRatio(c, HeroMossDeep).takeIf { it < 4.5 }?.let { "$n on HeroMossDeep = $it" } }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `button text reaches AA on rust and brass fills`() {
        assertTrue(contrastRatio(TextOnHero, AccentCopper) >= 4.5)
        assertTrue(contrastRatio(BrassInk, Brass) >= 4.5)
    }

    // T10b spec gap C: Archive's red-listed tag — StampNavy text on its own 12% tint (RedListTagBg
    // carries that alpha baked in, so composite using the color's own .alpha rather than a
    // second, possibly-drifting literal).
    @Test
    fun `red-listed tag text reaches AA on every paper surface`() {
        val failures =
            papers.mapNotNull { (pn, p) ->
                val backdrop = compositeOver(RedListTagBg, RedListTagBg.alpha, p)
                contrastRatio(StampNavy, backdrop).takeIf { it < 4.5 }?.let { "StampNavy tag on $pn = $it" }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
