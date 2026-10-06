package se.birdy.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Where the photo sits relative to the text block in [PhotoHero] (2026-10-06, Albin: the species
 * must be clearly visible). By default the photo fills the whole hero behind the text (the
 * Identify tab's daily bird). With `textBelowPhoto` (Match, species profile) the photo keeps its
 * own [PhotoHero] `height` at the top and the text block starts under it, overlapping only the
 * photo's faded bottom [TEXT_OVER_PHOTO], so the bird in the middle of the frame is never under
 * the text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class PhotoHeroLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    @Composable
    private fun Hero(
        tag: String,
        kicker: String,
        textBelowPhoto: Boolean,
        withPhoto: Boolean,
    ) = PhotoHero(
        kicker = kicker,
        title = "Talgoxe",
        latinName = "Parus major",
        height = 280.dp,
        bottomPadding = PaperSheetOverlap + 18.dp,
        textBelowPhoto = textBelowPhoto,
        modifier = Modifier.testTag(tag),
        image =
            if (withPhoto) {
                { Box(Modifier.fillMaxSize().testTag("$tag-photo")) }
            } else {
                null
            },
    )

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { BirdyTheme { Column { content() } } }
        compose.waitForIdle()
    }

    private fun assertClose(
        expected: Dp,
        actual: Dp,
        what: String,
    ) = assertTrue(abs((expected - actual).value) <= 1f, "$what: expected $expected, was $actual")

    @Test
    fun `by default the photo fills the whole hero behind the text`() {
        show { Hero("hero", "Mesar", textBelowPhoto = false, withPhoto = true) }
        val hero = compose.onNodeWithTag("hero").getUnclippedBoundsInRoot()
        val photo = compose.onNodeWithTag("hero-photo").getUnclippedBoundsInRoot()
        assertClose(hero.top, photo.top, "photo top")
        assertClose(hero.bottom, photo.bottom, "photo bottom")
    }

    @Test
    fun `with the text below the photo only the faded bottom edge of the photo is under the text`() {
        show { Hero("hero", "Mesar", textBelowPhoto = true, withPhoto = true) }
        val hero = compose.onNodeWithTag("hero").getUnclippedBoundsInRoot()
        val photo = compose.onNodeWithTag("hero-photo").getUnclippedBoundsInRoot()
        val kicker = compose.onNodeWithText("Mesar", ignoreCase = true).getUnclippedBoundsInRoot()
        assertClose(280.dp, photo.bottom - photo.top, "photo height")
        assertTrue(
            kicker.top >= photo.bottom - TEXT_OVER_PHOTO - 1.dp,
            "the text starts ${photo.bottom - kicker.top} above the photo's bottom, more than $TEXT_OVER_PHOTO",
        )
        assertTrue(hero.bottom > photo.bottom, "the text block should extend the hero below the photo")
    }

    @Test
    fun `without a photo the text-below option changes nothing`() {
        show {
            Hero("plain", "Mesar", textBelowPhoto = false, withPhoto = false)
            Hero("below", "Trastar", textBelowPhoto = true, withPhoto = false)
        }
        val plain = compose.onNodeWithTag("plain").getUnclippedBoundsInRoot()
        val below = compose.onNodeWithTag("below").getUnclippedBoundsInRoot()
        val kickerPlain = compose.onNodeWithText("Mesar", ignoreCase = true).getUnclippedBoundsInRoot()
        val kickerBelow = compose.onNodeWithText("Trastar", ignoreCase = true).getUnclippedBoundsInRoot()
        assertClose(plain.bottom - plain.top, below.bottom - below.top, "hero height")
        assertClose(kickerPlain.top - plain.top, kickerBelow.top - below.top, "kicker offset in the hero")
    }
}
