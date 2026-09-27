package se.birdy.app.ui.components

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.theme.BrassLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.compositeOver
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 AA for [PremiumTeaserCard]'s text at [premiumGlow]'s animated peak (T10b Critical 1).
 * [premiumGlow] sweeps a translucent white band across the card every 3.5s; a Roborazzi
 * screenshot always freezes the infinite transition at frame 0 (progress = 0) and can never show
 * the worst-case moment when the band's peak-alpha center sits directly behind the text — this is
 * pure-color math instead, mirroring [se.birdy.app.ui.premium.PremiumContrastTest].
 *
 * Models the backdrop as the glow's peak color composited over [HeroMossMid] — the LIGHTER end
 * of the card's gradient, so the worse case of the two — at [DARK_SURFACE_GLOW_PEAK_ALPHA], the
 * exact peakAlpha [PremiumTeaserCard] passes to `premiumGlow()`. Pins [PREMIUM_TEASER_SUBTITLE_ALPHA]
 * too, since the subtitle's own translucency stacks with the glow's.
 */
class PremiumTeaserCardContrastTest {
    @Test
    fun `teaser text clears AA against HeroMossMid at the glow's peak alpha`() {
        val backdrop = compositeOver(Color.White, DARK_SURFACE_GLOW_PEAK_ALPHA, HeroMossMid)
        val title = contrastRatio(TextOnHero, backdrop)
        val subtitle = contrastRatio(compositeOver(TextOnHero, PREMIUM_TEASER_SUBTITLE_ALPHA, backdrop), backdrop)
        val cta = contrastRatio(BrassLight, backdrop)

        val failures =
            listOfNotNull(
                "title $title < 4.5".takeIf { title < 4.5 },
                "subtitle $subtitle < 4.5".takeIf { subtitle < 4.5 },
                "cta $cta < 4.5".takeIf { cta < 4.5 },
            )
        assertTrue(failures.isEmpty(), "at peakAlpha $DARK_SURFACE_GLOW_PEAK_ALPHA: " + failures.joinToString("; "))
    }
}
