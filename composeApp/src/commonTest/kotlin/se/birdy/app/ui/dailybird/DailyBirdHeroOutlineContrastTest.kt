package se.birdy.app.ui.dailybird

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.components.TEXT_SCRIM_ALPHA
import se.birdy.app.ui.theme.PhotoScrim
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.compositeOver
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The daily-bird hero's outlined button sits on PhotoHero's text scrim (neutral black since
 * 2026-10-06). Its border is a graphical boundary, so WCAG 1.4.11's 3:1 applies, over the same
 * worst case as PhotoHeroContrastTest: a pure-white photo under the scrim.
 */
class DailyBirdHeroOutlineContrastTest {
    @Test
    fun `the outlined hero button's border clears 3 to 1 over a worst-case white photo`() {
        val backdrop = compositeOver(PhotoScrim, TEXT_SCRIM_ALPHA, Color.White)
        val border = contrastRatio(compositeOver(TextOnHero, HERO_OUTLINE_ALPHA, backdrop), backdrop)
        assertTrue(border >= 3.0, "outline $border < 3.0")
    }
}
