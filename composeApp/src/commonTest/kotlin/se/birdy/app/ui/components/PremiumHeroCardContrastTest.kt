package se.birdy.app.ui.components

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.compositeOver
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 AA for the Premium card at the top of Settings ([PremiumHeroCard], release 1.3.0
 * Task 7g item 5). Its rust accent word sat straight on the photo at about 1.5:1.
 *
 * The text now sits on a moss scrim ([PREMIUM_HERO_TEXT_SCRIM_ALPHA]) drawn above the photo and
 * the premium glow. The backdrop is modelled as that scrim over pure white: the brightest anything
 * under it can be (a blown-out photo, or the glow's 0.85 white band at its peak), so passing here
 * holds for every frame and every crop of the photo. Same model as [PhotoHeroContrastTest].
 */
class PremiumHeroCardContrastTest {
    private val worstCaseUnderScrim = Color.White

    @Test
    fun `premium card text clears AA over a white photo with only the scrim`() {
        val backdrop = compositeOver(PremiumHeroScrimColor, PREMIUM_HERO_TEXT_SCRIM_ALPHA, worstCaseUnderScrim)
        val headline = contrastRatio(PremiumHeroTextColor, backdrop)
        val accent = contrastRatio(PremiumHeroAccentColor, backdrop)
        val subline = contrastRatio(compositeOver(PremiumHeroTextColor, PREMIUM_HERO_SUBLINE_ALPHA, backdrop), backdrop)
        // The pill carries its own white fill under the rust label.
        val pill = contrastRatio(AccentCopper, Color.White)

        val failures =
            listOfNotNull(
                "headline $headline < 4.5".takeIf { headline < 4.5 },
                "accent $accent < 4.5".takeIf { accent < 4.5 },
                "subline $subline < 4.5".takeIf { subline < 4.5 },
                "pill $pill < 4.5".takeIf { pill < 4.5 },
            )
        assertTrue(failures.isEmpty(), "at scrim alpha $PREMIUM_HERO_TEXT_SCRIM_ALPHA: " + failures.joinToString("; "))
    }
}
