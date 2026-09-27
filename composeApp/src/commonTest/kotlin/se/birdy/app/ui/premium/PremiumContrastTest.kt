package se.birdy.app.ui.premium

import androidx.compose.ui.graphics.Color
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
 * backdrop for most of them. The tier-card title is the one exception: it sits on the card's own
 * translucent-white fill (`PREMIUM_TIER_FILL_*_ALPHA`), itself composited over [HeroMossDeep], so
 * that fill is the real backdrop for it — checked against the selected fill (more white mixed
 * in), the lower-contrast of the two states. Pins the exact alpha constants the screens use;
 * change both together.
 */
class PremiumContrastTest {
    @Test
    fun `premium screen text alphas clear AA on HeroMossDeep`() {
        val tierCardFill = compositeOver(Color.White, PREMIUM_TIER_FILL_SELECTED_ALPHA, HeroMossDeep)
        val cases =
            mapOf(
                "free item text @$PREMIUM_FREE_ITEM_TEXT_ALPHA" to
                    (compositeOver(TextOnHero, PREMIUM_FREE_ITEM_TEXT_ALPHA, HeroMossDeep) to HeroMossDeep),
                "feature sub-line @$PREMIUM_FEATURE_SUB_ALPHA" to
                    (compositeOver(TextOnHero, PREMIUM_FEATURE_SUB_ALPHA, HeroMossDeep) to HeroMossDeep),
                "tier card title @$PREMIUM_TIER_TITLE_ALPHA (on selected card fill)" to
                    (compositeOver(TextOnHero, PREMIUM_TIER_TITLE_ALPHA, tierCardFill) to tierCardFill),
                "auto-renew / lifetime note @$PREMIUM_NOTE_ALPHA" to
                    (compositeOver(TextOnHero, PREMIUM_NOTE_ALPHA, HeroMossDeep) to HeroMossDeep),
                "thank-you body @$PREMIUM_THANKS_BODY_ALPHA" to
                    (compositeOver(TextOnHero, PREMIUM_THANKS_BODY_ALPHA, HeroMossDeep) to HeroMossDeep),
            )
        val failures =
            cases.mapNotNull { (name, pair) ->
                val (text, backdrop) = pair
                contrastRatio(text, backdrop).takeIf { it < 4.5 }?.let { "$name = $it" }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
