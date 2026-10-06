package se.birdy.app.ui.diary

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.PhotoScrim
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.compositeOver
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG for [RecapEntryCard]'s overlay text (review fix wave T8c, 2026-09-27; neutral scrim since
 * 2026-10-06). The card's kicker/title/sub sit in the card's left ~75% (after the week-number
 * circle); the decorative chevron sits at the far right edge. Same "pure color math, no compose
 * measurer" approach as [se.birdy.app.ui.components.PhotoHeroContrastTest] — this is a
 * commonTest, there is no layout pass available, and a blown-out white photo is the strict worst
 * case for light text over a darkening scrim.
 *
 * The text block's worst position within `[0, RECAP_OVERLAY_MID_STOP]` is that stop's own low
 * end ([RECAP_OVERLAY_MID_ALPHA]) — moving further left the gradient is never less opaque — so
 * that alpha is a true lower bound for wherever the text block actually sits, not a guess at one
 * layout. The chevron sits at the gradient's far end ([RECAP_OVERLAY_FAR_ALPHA]) exactly.
 */
class RecapEntryCardContrastTest {
    private val worstCasePhoto = Color(0xFFFFFFFF)
    private val textBackdrop = compositeOver(PhotoScrim, RECAP_OVERLAY_MID_ALPHA, worstCasePhoto)
    private val chevronBackdrop = compositeOver(PhotoScrim, RECAP_OVERLAY_FAR_ALPHA, worstCasePhoto)

    @Test
    fun `kicker and sub clear normal-text AA over a worst-case white photo`() {
        val kicker = contrastRatio(AccentCopperLight, textBackdrop)
        val sub = contrastRatio(compositeOver(TextOnHero, 0.8f, textBackdrop), textBackdrop)
        assertTrue(kicker >= 4.5, "kicker $kicker < 4.5")
        assertTrue(sub >= 4.5, "sub $sub < 4.5")
    }

    @Test
    fun `title clears normal-text AA over a worst-case white photo`() {
        // 20sp regular doesn't meet WCAG's "large text" size (18pt/24px regular, or 14pt/18.66px
        // bold) — the 4.5:1 normal-text minimum applies, not the relaxed 3:1 large-text one.
        val title = contrastRatio(TextOnHero, textBackdrop)
        assertTrue(title >= 4.5, "title $title < 4.5")
    }

    @Test
    fun `decorative chevron clears the 3-to-1 graphical-object minimum`() {
        // Not real text — clearAndSetSemantics hides it from screen readers — so WCAG 1.4.11
        // (non-text contrast for graphical objects), not the 4.5:1 text minimum, applies.
        val chevron = contrastRatio(TextOnHero, chevronBackdrop)
        assertTrue(chevron >= 3.0, "chevron $chevron < 3.0")
    }

    @Test
    fun `the text stop is no darker than AA needs`() {
        // The overlay sits on the find photos; it should dim them only as far as the kicker
        // (the weakest text color) needs. One step (0.05) lighter must fail.
        val lighter = compositeOver(PhotoScrim, RECAP_OVERLAY_MID_ALPHA - 0.05f, worstCasePhoto)
        assertTrue(contrastRatio(AccentCopperLight, lighter) < 4.5, "RECAP_OVERLAY_MID_ALPHA could be lighter")
    }

    @Test
    fun `the overlay is never lighter left of the mid stop`() {
        // The premise of the lower bound above (see the class KDoc).
        assertTrue(RECAP_OVERLAY_NEAR_ALPHA >= RECAP_OVERLAY_MID_ALPHA, "near $RECAP_OVERLAY_NEAR_ALPHA < mid $RECAP_OVERLAY_MID_ALPHA")
    }
}
