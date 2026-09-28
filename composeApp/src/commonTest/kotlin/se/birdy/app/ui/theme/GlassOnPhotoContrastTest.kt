package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [GlassOnPhoto] is a translucent dark disc drawn directly on a photo (behind [GlassIconButton]'s
 * icon) — unlike the rest of a [se.birdy.app.ui.components.PhotoHero]'s text, it gets NO credit
 * from the hero's global or text-following scrim (a top-right icon can sit above both). This pins
 * a lower bound: [TextOnHero] on [GlassOnPhoto] composited directly over a worst-case (pure white,
 * blown-out) photo must still clear a 3:1 minimum (WCAG AA for a large/iconic graphical object,
 * not the 4.5:1 body-text threshold [ColorContrastTest] uses).
 *
 * See [GlassOnPhoto]'s own KDoc in `Color.kt` for the alpha history (0.30 measured against one
 * bundled photo's non-worst-case region read ≈6.7:1 there but only ≈2.0:1 against pure white with
 * no scrim credit — 0.45 was picked to clear this test).
 */
class GlassOnPhotoContrastTest {
    @Test
    fun `TextOnHero clears a 3 to 1 minimum on GlassOnPhoto over a worst-case white photo`() {
        val backdrop = compositeOver(Color.Black, GlassOnPhoto.alpha, Color.White)
        val ratio = contrastRatio(TextOnHero, backdrop)
        assertTrue(ratio >= 3.0, "TextOnHero on GlassOnPhoto over worst-case white = $ratio < 3.0")
    }
}
