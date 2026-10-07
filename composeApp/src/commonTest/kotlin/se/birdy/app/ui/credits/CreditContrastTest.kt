package se.birdy.app.ui.credits

import se.birdy.app.ui.components.META_TEXT_ALPHA
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.PhotoBand
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.compositeOver
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The credits are small text (11sp), so they need WCAG AA 4.5:1 where they sit: right under the
 * photo on the plain [PhotoBand] (never over the photo), and on the paper of the profile's sheet
 * and the Disambig cards. Links share the text's color and are underlined.
 */
class CreditContrastTest {
    @Test
    fun `the credit under a photo clears AA on the band`() {
        val ratio = contrastRatio(compositeOver(TextOnHero, META_TEXT_ALPHA, PhotoBand), PhotoBand)
        assertTrue(ratio >= 4.5, "credit on PhotoBand: $ratio")
    }

    @Test
    fun `the credit on paper clears AA on the sheet and on a card`() {
        for (paper in listOf(MossCreme, CardPaper)) {
            val ratio = contrastRatio(CreditOnPaper, paper)
            assertTrue(ratio >= 4.5, "credit on $paper: $ratio")
        }
    }
}
