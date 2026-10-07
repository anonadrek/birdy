package se.birdy.app.ui.settings.credits

import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.PhotoCredit
import kotlin.test.assertEquals

/**
 * Release 1.3.0 Task 7e-2 (legal review §2): the "Bildkällor" page lists every photo with its
 * photographer, licence and Commons page, CC0 and public domain included; a row opens the photo's
 * file page and the licence opens the licence deed, also for TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PhotoCreditsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun credit(
        id: String,
        name: String,
        role: String,
        path: String,
        license: String,
        author: String,
        file: String,
    ) = PhotoCredit(SpeciesId(id), name, "Scientific $name", role, path, license, author, file)

    private val credits =
        listOf(
            credit("Q25485", "Talgoxe", "secondary", "Q25485/secondary-1.webp", "CC BY 2.0", "Anton Whoa", "Parus major, Omsk, Russia.jpg"),
            credit(
                "Q25485",
                "Talgoxe",
                "hero",
                "Q25485/hero.webp",
                "CC0",
                "Hobbyfotowiki",
                "Great tit (Parus major), North Rhine-Westphalia.jpg",
            ),
            credit("Q25404", "Blåmes", "hero", "Q25404/hero.webp", "CC BY 2.0", "Kathy Büscher", "Blaumeise (64) (34633517080).jpg"),
            credit("Q1", "Ärtsångare", "hero", "Q1/hero.webp", "Public domain", "U.S. Fish and Wildlife Service", "Sylvia curruca.jpg"),
        )

    private fun show() {
        attachComposeResourcesContext()
        compose.setContent {
            BirdyTheme {
                PhotoCreditsScreen(
                    state = Loadable.Loaded(groupPhotoCredits(credits)),
                    locale = Locale.SV,
                    onBack = {},
                    onOpenUrl = { opened += it },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `photos are grouped per species from A to Ö, the main photo first`() {
        val grouped = groupPhotoCredits(credits)
        assertEquals(4, grouped.total)
        assertEquals(listOf("Blåmes", "Talgoxe", "Ärtsångare"), grouped.groups.map { it.speciesName })
        assertEquals(listOf("Q25485/hero.webp", "Q25485/secondary-1.webp"), grouped.groups[1].photos.map { it.path })
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a row shows the photographer and licence and opens the photo on commons`() {
        show()
        compose.onNodeWithText("Alla 4 artfoton i Birdy kommer från Wikimedia Commons", substring = true).assertExists()
        val description = "Talgoxe, Huvudbild. Foto: Hobbyfotowiki. Licens: CC0. Wikimedia Commons, nedskalad."
        compose.onNodeWithContentDescription(description).performScrollTo().performClick()
        compose
            .onNodeWithContentDescription("Talgoxe, Bild 2. Foto: Anton Whoa. Licens: CC BY 2.0. Wikimedia Commons, nedskalad.")
            .assertExists()
        compose.runOnIdle {
            assertEquals(
                listOf("https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg"),
                opened,
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Config(qualifiers = "+sv")
    fun `talkback opens the licence through an action on the row`() {
        show()
        compose
            .onNodeWithContentDescription("Blåmes, Huvudbild. Foto: Kathy Büscher. Licens: CC BY 2.0. Wikimedia Commons, nedskalad.")
            .performCustomAccessibilityActionWithLabel("Öppna licensen CC BY 2.0")
        compose.runOnIdle { assertEquals(listOf("https://creativecommons.org/licenses/by/2.0/"), opened) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a tap on the licence name opens the licence, not the photo`() {
        show()
        val row =
            compose.onNodeWithContentDescription(
                "Blåmes, Huvudbild. Foto: Kathy Büscher. Licens: CC BY 2.0. Wikimedia Commons, nedskalad.",
            )
        // The licence line is the row's last line of text: 8 dp of padding, the 1 dp rule and its
        // 8 dp gap below it, and half a 13 sp line (about 9 dp) from the bottom of the row.
        row.performTouchInput {
            val dp = density
            click(Offset(4 * dp, height - 26 * dp))
        }
        compose.runOnIdle { assertEquals(listOf("https://creativecommons.org/licenses/by/2.0/"), opened) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the licence in the row's line is a link to its deed`() {
        attachComposeResourcesContext()
        compose.setContent {
            BirdyTheme {
                Text(licenseLine("CC BY 2.0", "https://creativecommons.org/licenses/by/2.0/", "nedskalad") { opened += it })
            }
        }
        compose.onNodeWithText("CC BY 2.0 · Wikimedia Commons · nedskalad").performFirstLinkClick()
        compose.runOnIdle { assertEquals(listOf("https://creativecommons.org/licenses/by/2.0/"), opened) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a public domain photo is credited too, with no licence to open`() {
        show()
        compose
            .onNodeWithContentDescription(
                "Ärtsångare, Huvudbild. Foto: U.S. Fish and Wildlife Service. Licens: Public domain. Wikimedia Commons, nedskalad.",
            ).performScrollTo()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the english page`() {
        show()
        compose.onNodeWithText("All 4 species photos in Birdy come from Wikimedia Commons", substring = true).assertExists()
        compose
            .onNodeWithContentDescription("Talgoxe, Main photo. Photo: Hobbyfotowiki. License: CC0. Wikimedia Commons, resized.")
            .assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the photos outside the species list are credited under other images`() {
        show()
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("other")
        compose.onNodeWithText("Övriga bilder").assertExists()
        // The premium photo is named like the species above it (Talgoxe), the test image by its
        // scientific name when its species is not in the list.
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("other/premium/great-tit-hero.jpg")
        compose
            .onNodeWithContentDescription("Talgoxe, Premiumskärmen. Foto: Hobbyfotowiki. Licens: CC0. Wikimedia Commons, nedskalad.")
            .performClick()
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("other/testdata/parity_Q180991.jpg")
        compose
            .onNodeWithContentDescription(
                "Mergus merganser, Testbild för fotomodellen. Foto: Hobbyfotowiki. Licens: CC0. Wikimedia Commons, nedskalad.",
            ).assertExists()
        compose.runOnIdle {
            assertEquals(
                listOf("https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg"),
                opened,
            )
        }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a page that cannot be read says so`() {
        attachComposeResourcesContext()
        compose.setContent {
            BirdyTheme { PhotoCreditsScreen(state = Loadable.Failed, locale = Locale.SV, onBack = {}) }
        }
        compose.onNodeWithText("Bildkällorna gick inte att läsa.").assertExists()
    }
}
