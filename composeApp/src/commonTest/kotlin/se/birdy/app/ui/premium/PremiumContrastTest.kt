package se.birdy.app.ui.premium

import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.compositeOver
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 AA for the Premium + thank-you screens' body text (Task 9, spec 2026-09-24). Unlike
 * [se.birdy.app.ui.components.PhotoHeroContrastTest], these sit directly on the flat
 * [HeroMossDeep] surface — not over a photo/scrim — so compositing straight over it is the real
 * backdrop, not a worst-case lower bound. Pins the exact alpha constants the screens use; change
 * both together.
 */
class PremiumContrastTest {
    @Test
    fun `premium screen text alphas clear AA on HeroMossDeep`() {
        val cases =
            mapOf(
                "free item text @$PREMIUM_FREE_ITEM_TEXT_ALPHA" to
                    compositeOver(TextOnHero, PREMIUM_FREE_ITEM_TEXT_ALPHA, HeroMossDeep),
                "feature sub-line @$PREMIUM_FEATURE_SUB_ALPHA" to
                    compositeOver(TextOnHero, PREMIUM_FEATURE_SUB_ALPHA, HeroMossDeep),
                "tier card title @$PREMIUM_TIER_TITLE_ALPHA" to
                    compositeOver(TextOnHero, PREMIUM_TIER_TITLE_ALPHA, HeroMossDeep),
                "auto-renew / lifetime note @$PREMIUM_NOTE_ALPHA" to
                    compositeOver(TextOnHero, PREMIUM_NOTE_ALPHA, HeroMossDeep),
                "thank-you body @$PREMIUM_THANKS_BODY_ALPHA" to
                    compositeOver(TextOnHero, PREMIUM_THANKS_BODY_ALPHA, HeroMossDeep),
            )
        val failures =
            cases.mapNotNull { (name, color) ->
                contrastRatio(color, HeroMossDeep).takeIf { it < 4.5 }?.let { "$name = $it" }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
