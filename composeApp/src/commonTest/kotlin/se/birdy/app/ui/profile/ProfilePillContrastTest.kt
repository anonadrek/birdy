package se.birdy.app.ui.profile

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.components.TEXT_SCRIM_ALPHA
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.PhotoBand
import se.birdy.app.ui.theme.PhotoScrim
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.compositeOver
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 AA for the species-profile hero's abundance/family/IUCN pill row (Task 11, spec
 * 2026-09-24 §4.3) — a translucent LIGHT fill drawn in [se.birdy.app.ui.components.PhotoHero]'s
 * `bottomContent` slot. [se.birdy.app.ui.components.PhotoHero]'s own KDoc calls this out: a light
 * fill LIGHTENS the backdrop under itself instead of darkening it, unlike the rest of the
 * header's text, so [se.birdy.app.ui.components.PhotoHeroContrastTest]'s text-scrim-alone premise
 * doesn't cover it — this is that check.
 *
 * Models the same worst-case (pure white) photo as PhotoHeroContrastTest, composited first with
 * the text-following scrim (the real backdrop under the pill row), then with the pill's own
 * glass fill on top of that for the unfilled variant. The filled (abundance) pill is an opaque
 * [AccentCopper] fill, so it's checked directly against that color.
 */
class ProfilePillContrastTest {
    // Pure white — the strict worst case for a photo behind light text (see PhotoHeroContrastTest).
    private val worstCasePhoto = Color(0xFFFFFFFF)

    @Test
    fun `pill text clears AA on both the glass fill and the filled abundance pill`() {
        val textScrimBackdrop = compositeOver(PhotoScrim, TEXT_SCRIM_ALPHA, worstCasePhoto)
        val glassPillFill = compositeOver(Color.White, PROFILE_PILL_GLASS_ALPHA, textScrimBackdrop)

        val glass = contrastRatio(TextOnHero, glassPillFill)
        val filled = contrastRatio(TextOnHero, AccentCopper)

        val failures =
            listOfNotNull(
                "glass pill $glass < 4.5".takeIf { glass < 4.5 },
                "filled pill $filled < 4.5".takeIf { filled < 4.5 },
            )
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    // Since 2026-10-06 the species profile puts its text below the photo (PhotoHero's
    // textBelowPhoto), so the pill row actually sits on the plain PhotoBand.
    @Test
    fun `glass pill text clears AA on the band below the photo`() {
        val glass = contrastRatio(TextOnHero, compositeOver(Color.White, PROFILE_PILL_GLASS_ALPHA, PhotoBand))
        assertTrue(glass >= 4.5, "glass pill on PhotoBand $glass < 4.5")
    }
}
