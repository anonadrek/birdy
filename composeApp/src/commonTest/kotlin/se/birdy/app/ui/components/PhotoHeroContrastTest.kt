package se.birdy.app.ui.components

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.compositeOver
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 AA for [PhotoHero]'s bottom-aligned text over a worst-case LIGHT photo (spec
 * 2026-09-24 §4.3, strengthened by the 2026-09-26 fix wave A2 review, finding I3: the kicker/
 * latinName/subtitle/meta text only cleared 1.5–4.6:1 over an overcast-sky photo before this
 * fix). This is a pure-color/pure-math regression test — a commonTest has no compose measurer,
 * so it can't lay the real component out — modeled the same way [se.birdy.app.ui.theme.ColorContrastTest]
 * treats color math independently of any real layout pass.
 *
 * The modeled scenario is deliberately the WORSE of the two this suite screenshots
 * (see ComponentsScreenshotTest.hero_photo_sv / hero_long_en_130): a 2-line title (at its
 * un-shrunk 44sp lineHeight — many 2-line names never trigger TextAutoSize's step-down, since
 * wrapping to exactly 2 lines isn't an overflow) + a latinName + a subtitle + a meta row, all
 * at 130% system font scale. sp line heights scale with font scale; the dp paddings around them
 * don't. If PhotoHero's real text block ever starts higher (a smaller fraction from the top)
 * than this budget assumes, this test is the tripwire — tighten heroScrimAlphaAt's stops to
 * match.
 */
class PhotoHeroContrastTest {
    private val fontScale = 1.3f

    // Fixed dp paddings around the text block — unaffected by font scale.
    private val kickerSpacerDp = 8f
    private val latinNameTopPaddingDp = 2f
    private val subtitleTopPaddingDp = 4f
    private val metaRowTopPaddingDp = 12f + 1f + 8f // divider top padding + 1dp rule + text top padding
    private val defaultBottomPaddingDp = 18f

    // sp line heights, scaled by fontScale (sp tracks the system font scale; dp doesn't).
    private val kickerLineHeightSp = 12f * fontScale
    private val titleLineHeightSp = 44f * fontScale // HeroTitle's un-shrunk (42sp) lineHeight
    private val latinNameLineHeightSp = 18f * fontScale
    private val subtitleLineHeightSp = 22f * fontScale // inherited bodyLarge lineHeight (20sp font — see MetaText's comment)
    private val metaLineHeightSp = 12f * fontScale

    private val heroHeightDp = 300f // PhotoHero's default/minimum `height`

    /** Where (as a fraction from the hero's top) the text block's topmost line — the kicker — can start. */
    private fun conservativeTopFraction(): Float {
        val textBlockHeight =
            kickerLineHeightSp + kickerSpacerDp +
                titleLineHeightSp * 2 +
                latinNameTopPaddingDp + latinNameLineHeightSp +
                subtitleTopPaddingDp + subtitleLineHeightSp +
                metaRowTopPaddingDp + metaLineHeightSp +
                defaultBottomPaddingDp
        return ((heroHeightDp - textBlockHeight) / heroHeightDp).coerceAtLeast(0f)
    }

    // An overcast-sky photo color — the reviewer's reference for the worst realistic photo.
    private val lightReferencePhoto = Color(0xFFD6DBE0)

    @Test
    fun `hero text clears AA over a light reference photo at the conservative text-block position`() {
        val fraction = conservativeTopFraction()
        val alpha = heroScrimAlphaAt(fraction)
        val backdrop = compositeOver(HeroMossDeep, alpha, lightReferencePhoto)

        val kicker = contrastRatio(AccentCopperLight, backdrop)
        val latinName = contrastRatio(compositeOver(TextOnHero, LATIN_NAME_TEXT_ALPHA, backdrop), backdrop)
        val subtitle = contrastRatio(AccentCopperLight, backdrop)
        val meta = contrastRatio(compositeOver(TextOnHero, META_TEXT_ALPHA, backdrop), backdrop)
        val title = contrastRatio(TextOnHero, backdrop)

        val failures =
            listOfNotNull(
                "kicker $kicker < 4.5".takeIf { kicker < 4.5 },
                "latinName $latinName < 4.5".takeIf { latinName < 4.5 },
                "subtitle $subtitle < 4.5".takeIf { subtitle < 4.5 },
                "meta $meta < 4.5".takeIf { meta < 4.5 },
                "title (large text) $title < 3.0".takeIf { title < 3.0 },
            )
        assertTrue(
            failures.isEmpty(),
            "at fraction $fraction (scrim alpha $alpha): " + failures.joinToString("; "),
        )
    }
}
