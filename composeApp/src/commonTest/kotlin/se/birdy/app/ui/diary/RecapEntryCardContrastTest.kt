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
 * WCAG for [RecapEntryCard]'s overlay text (review fix wave T8c, 2026-09-27; neutral and flat
 * since 2026-10-06). Same "pure color math, no compose measurer" approach as
 * [se.birdy.app.ui.components.PhotoHeroContrastTest] — this is a commonTest, there is no layout
 * pass available, and a blown-out white photo is the strict worst case for light text over a
 * darkening scrim.
 *
 * The overlay is ONE flat [RECAP_OVERLAY_ALPHA] over the whole card. It used to fade lighter
 * towards the right edge on the assumption that the text column ends at 75% of the card width,
 * but the column is `weight(1f)` and ends `14 + 12dp + chevron` from the right edge: about 89%
 * on a narrow phone, 91% at 411dp and over 95% on a tablet. No fixed stop bounds that, so the
 * overlay is flat and this backdrop holds wherever the kicker, title, sub and chevron sit.
 */
class RecapEntryCardContrastTest {
    private val worstCasePhoto = Color(0xFFFFFFFF)
    private val backdrop = compositeOver(PhotoScrim, RECAP_OVERLAY_ALPHA, worstCasePhoto)

    @Test
    fun `kicker and sub clear normal-text AA over a worst-case white photo`() {
        val kicker = contrastRatio(AccentCopperLight, backdrop)
        val sub = contrastRatio(compositeOver(TextOnHero, 0.8f, backdrop), backdrop)
        assertTrue(kicker >= 4.5, "kicker $kicker < 4.5")
        assertTrue(sub >= 4.5, "sub $sub < 4.5")
    }

    @Test
    fun `title clears normal-text AA over a worst-case white photo`() {
        // 20sp regular doesn't meet WCAG's "large text" size (18pt/24px regular, or 14pt/18.66px
        // bold) — the 4.5:1 normal-text minimum applies, not the relaxed 3:1 large-text one.
        val title = contrastRatio(TextOnHero, backdrop)
        assertTrue(title >= 4.5, "title $title < 4.5")
    }

    @Test
    fun `decorative chevron clears the 3-to-1 graphical-object minimum`() {
        // Not real text — clearAndSetSemantics hides it from screen readers — so WCAG 1.4.11
        // (non-text contrast for graphical objects), not the 4.5:1 text minimum, applies.
        val chevron = contrastRatio(TextOnHero, backdrop)
        assertTrue(chevron >= 3.0, "chevron $chevron < 3.0")
    }

    @Test
    fun `the overlay is no darker than AA needs`() {
        // The overlay sits on the find photos; it should dim them only as far as the kicker
        // (the weakest text color) needs. One step (0.05) lighter must fail.
        val lighter = compositeOver(PhotoScrim, RECAP_OVERLAY_ALPHA - 0.05f, worstCasePhoto)
        assertTrue(contrastRatio(AccentCopperLight, lighter) < 4.5, "RECAP_OVERLAY_ALPHA could be lighter")
    }
}
