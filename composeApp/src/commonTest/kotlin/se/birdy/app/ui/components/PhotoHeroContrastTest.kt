package se.birdy.app.ui.components

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.PhotoBand
import se.birdy.app.ui.theme.PhotoLoading
import se.birdy.app.ui.theme.PhotoScrim
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.compositeOver
import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WCAG 2.1 AA for [PhotoHero]'s bottom-aligned text over the worst-case photo (spec 2026-09-24
 * §4.3), and the rule that nothing drawn over a bird photo has a hue (2026-10-06, Albin: the moss
 * green over the bird made the species hard to see).
 *
 * The only thing drawn over the photo where text sits is the text-following scrim (see
 * [PhotoHero]'s `drawTextFollowingScrim`): [PhotoScrim] fading in above the text Column's top
 * edge, then FLAT at [TEXT_SCRIM_ALPHA] all the way to the bottom. Because it is flat, the same
 * single backdrop color covers every line (kicker, latinName, subtitle, meta and title all sit
 * on it), wherever the text block starts. The thin status-bar scrim at the very top never
 * reaches the text and is not counted. With `textBelowPhoto` (Match, species profile) there is
 * no scrim at all: the text starts below the photo, on the plain [PhotoBand].
 *
 * The reference photo is pure white (fix wave A2c, 2026-09-27), not an overcast-sky gray: a
 * blown-out sky or snow is a realistic bird photo, and for light text over a darkening scrim,
 * white is the strict worst case (the brightest anything can be) — so this is a true lower bound
 * for every photo. The text shadow is a practical aid on top and is not counted here.
 *
 * This is pure-color/pure-math — a commonTest has no compose measurer, so it can't lay the real
 * component out — modeled the same way [se.birdy.app.ui.theme.ColorContrastTest] treats color
 * math independently of any real layout pass.
 */
class PhotoHeroContrastTest {
    // Pure white — the strict worst case for a photo behind light text (see class KDoc).
    private val worstCasePhoto = Color(0xFFFFFFFF)

    private fun failuresOn(backdrop: Color): List<String> {
        val kicker = contrastRatio(AccentCopperLight, backdrop)
        val latinName = contrastRatio(compositeOver(TextOnHero, LATIN_NAME_TEXT_ALPHA, backdrop), backdrop)
        val subtitle = contrastRatio(AccentCopperLight, backdrop)
        val meta = contrastRatio(compositeOver(TextOnHero, META_TEXT_ALPHA, backdrop), backdrop)
        val title = contrastRatio(TextOnHero, backdrop)
        return listOfNotNull(
            "kicker $kicker < 4.5".takeIf { kicker < 4.5 },
            "latinName $latinName < 4.5".takeIf { latinName < 4.5 },
            "subtitle $subtitle < 4.5".takeIf { subtitle < 4.5 },
            "meta $meta < 4.5".takeIf { meta < 4.5 },
            "title (large text) $title < 3.0".takeIf { title < 3.0 },
        )
    }

    @Test
    fun `hero text clears AA over a worst-case white photo with only the text scrim`() {
        val failures = failuresOn(compositeOver(PhotoScrim, TEXT_SCRIM_ALPHA, worstCasePhoto))
        assertTrue(failures.isEmpty(), "at scrim alpha $TEXT_SCRIM_ALPHA: " + failures.joinToString("; "))
    }

    // The scrim darkens the photo under the text, so it must be as light as AA allows: one step
    // (0.05) lighter has to fail, or the bird is dimmed for nothing.
    @Test
    fun `text scrim alpha is the lightest step that still clears AA`() {
        val lighter = TEXT_SCRIM_ALPHA - 0.05f
        val failures = failuresOn(compositeOver(PhotoScrim, lighter, worstCasePhoto))
        assertTrue(failures.isNotEmpty(), "alpha $lighter also clears AA, so $TEXT_SCRIM_ALPHA darkens the photo more than needed")
    }

    @Test
    fun `nothing over or behind a bird photo has a hue`() {
        for ((name, color) in listOf("PhotoScrim" to PhotoScrim, "PhotoLoading" to PhotoLoading, "PhotoBand" to PhotoBand)) {
            assertEquals(color.red, color.green, "$name has a hue: $color")
            assertEquals(color.green, color.blue, "$name has a hue: $color")
        }
    }

    // textBelowPhoto (Match, species profile): the text starts at the photo's bottom edge and
    // sits on the plain PhotoBand, with no scrim and no photo under it at all.
    @Test
    fun `hero text clears AA on the band below the photo without any scrim`() {
        val failures = failuresOn(PhotoBand)
        assertTrue(failures.isEmpty(), "on PhotoBand: " + failures.joinToString("; "))
    }

    // No photo (a species without one): the hero is the moss gradient and draws no scrim at all,
    // so the text sits straight on the gradient. Its lightest stop is the worst case.
    @Test
    fun `hero text clears AA on the no-photo moss gradient without any scrim`() {
        val failures = failuresOn(HeroMossLight)
        assertTrue(failures.isEmpty(), "on HeroMossLight: " + failures.joinToString("; "))
    }
}
