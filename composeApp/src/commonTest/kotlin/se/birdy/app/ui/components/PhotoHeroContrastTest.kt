package se.birdy.app.ui.components

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.compositeOver
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 AA for [PhotoHero]'s bottom-aligned text over a worst-case LIGHT photo (spec
 * 2026-09-24 §4.3). Fix wave A2b (2026-09-27) replaced the old hero-relative scrim ramp — which
 * had to be strengthened so much it made the photo nearly invisible (fix wave A2, finding I3) —
 * with a scrim that follows the text block itself: it fades in above the text Column's top edge,
 * then stays FLAT at [TEXT_SCRIM_ALPHA] all the way to the bottom (see [PhotoHero]'s drawBehind /
 * `drawTextFollowingScrim`).
 *
 * This models the backdrop with ONLY that text scrim, treating the separate global scrim (the
 * light mockup gradient painted across the whole photo) as fully transparent. That is a
 * deliberate LOWER BOUND, not a guess at one specific layout: the text block can start anywhere
 * in the hero, including inside the global scrim's own transparent band, and everywhere the
 * global scrim DOES contribute alpha it only adds more darkness under light text — which can
 * only raise contrast further. So proving AA with the global scrim zeroed out proves it for
 * every real position, and — because the text scrim is flat rather than ramped — the same single
 * backdrop color covers every line (kicker, latinName, subtitle, meta and title all sit on it).
 *
 * This is pure-color/pure-math — a commonTest has no compose measurer, so it can't lay the real
 * component out — modeled the same way [se.birdy.app.ui.theme.ColorContrastTest] treats color
 * math independently of any real layout pass.
 */
class PhotoHeroContrastTest {
    // An overcast-sky photo color — the reviewer's reference for the worst realistic photo.
    private val lightReferencePhoto = Color(0xFFD6DBE0)

    @Test
    fun `hero text clears AA over a light reference photo with only the text scrim`() {
        val backdrop = compositeOver(HeroMossDeep, TEXT_SCRIM_ALPHA, lightReferencePhoto)

        val kicker = contrastRatio(AccentCopperLight, backdrop)
        val latinName = contrastRatio(compositeOver(TextOnHero, LATIN_NAME_TEXT_ALPHA, backdrop), backdrop)
        val subtitle = contrastRatio(AccentCopperLight, backdrop)
        val meta = contrastRatio(compositeOver(TextOnHero, META_TEXT_ALPHA, backdrop), backdrop)
        val title = contrastRatio(TextOnHero, backdrop)

        val failures =
            listOfNotNull(
                "kicker $kicker < 4.5".takeIf { kicker < 4.5 },
                "latinName $latinName < 4.5".takeIf { latinName < 4.5 },
                "subtitle $subtitle < 4.5".takeIf { subtitle < 4.5 },
                "meta $meta < 4.5".takeIf { meta < 4.5 },
                "title (large text) $title < 3.0".takeIf { title < 3.0 },
            )
        assertTrue(
            failures.isEmpty(),
            "at scrim alpha $TEXT_SCRIM_ALPHA: " + failures.joinToString("; "),
        )
    }
}
