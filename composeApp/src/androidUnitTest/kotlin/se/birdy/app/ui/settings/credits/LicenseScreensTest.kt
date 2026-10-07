package se.birdy.app.ui.settings.credits

import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0, legal review 7i-fix B: the "Licenser för öppen källkod" page lists the bundled
 * licence index, and an entry opens its full text, read from the app's own resources.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LicenseScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+sv")
    fun `the list shows the models and libraries and opens an entry`() {
        attachComposeResourcesContext()
        val index = runBlocking { LicenseFiles.index() }
        val openedEntries = mutableListOf<String>()
        compose.setContent {
            BirdyTheme {
                OpenSourceLicensesScreen(state = Loadable.Loaded(index), onBack = {}, onOpenEntry = { openedEntries += it })
            }
        }
        compose.onNodeWithText("MODELLER").assertExists()
        compose.onNodeWithText("Version 6K global model · CC BY-NC-SA 4.0").assertExists()
        compose.onNodeWithText("BirdNET-Lite").performClick()
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("lib:org.osmdroid:6.1.20")
        compose.onNodeWithText("org.osmdroid").performClick()
        compose.runOnIdle { assertEquals(listOf("birdnet-lite", "lib:org.osmdroid:6.1.20"), openedEntries) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `an entry shows its notice and the full licence text`() {
        attachComposeResourcesContext()
        val text = runBlocking { loadLicenseText("aiy-birds-v1") }
        assertTrue(text.notice!!.startsWith("AIY Birds V1"))
        assertTrue(text.paragraphs.any { "Version 2.0, January 2004" in it })
        val opened = mutableListOf<String>()
        compose.setContent {
            BirdyTheme { LicenseTextScreen(state = Loadable.Loaded(text), onBack = {}, onOpenUrl = { opened += it }) }
        }
        compose.onNodeWithText("Version birds_V1/3 · Apache License 2.0").assertExists()
        compose.onNodeWithText("Webbplats").performFirstLinkClick()
        compose.onNodeWithText("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION", substring = true).performScrollTo()
        compose.runOnIdle {
            assertEquals(listOf("https://tfhub.dev/google/lite-model/aiy/vision/classifier/birds_V1/3"), opened)
        }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a font shows its copyright lines`() {
        attachComposeResourcesContext()
        val text = runBlocking { loadLicenseText("font:caveat") }
        compose.setContent { BirdyTheme { LicenseTextScreen(state = Loadable.Loaded(text), onBack = {}) } }
        compose.onNodeWithText("Copyright 2014 The Caveat Project Authors", substring = true).assertExists()
        compose.onNodeWithText("SIL OPEN FONT LICENSE Version 1.1", substring = true).assertExists()
    }

    @Test
    fun `licence texts split into paragraphs at blank lines`() {
        assertEquals(listOf("A\nb", "C"), licenseParagraphs("A\nb\n\n  \nC\n"))
        assertEquals(listOf("Title", "   1. Indented."), licenseParagraphs("Title\n\n   1. Indented.\n"))
    }

    @Test
    fun `lines wrapped for a narrow file are joined, structure is kept`() {
        assertEquals(
            "The goals of the OFL are to stimulate worldwide development.",
            reflow("The goals of the OFL are\nto stimulate worldwide\ndevelopment."),
        )
        assertEquals("PREAMBLE\nThe goals are these.", reflow("PREAMBLE\nThe goals\nare these."))
        assertEquals("Conditions:\n1. Keep this.\n2. And this, all of it.", reflow("Conditions:\n1. Keep this.\n2. And this,\nall of it."))
        assertEquals("    * Retain the copyright notice, this list.", reflow("    * Retain the copyright\nnotice, this list."))
        assertEquals("Protocol Buffers\n================", reflow("Protocol Buffers\n================"))
        assertEquals("   indented\n   kept", reflow("   indented\n   kept"))
        assertEquals("-----\nSIL OPEN FONT LICENSE Version 1.1", reflow("-----\nSIL OPEN FONT LICENSE Version 1.1"))
        assertEquals(
            "Licence: MIT\nIn the flex library: version 1\nSource of this text: https://x",
            reflow("Licence: MIT\nIn the flex library: version 1\nSource of this text: https://x"),
        )
        assertEquals(
            "Copyright 2019 Google LLC\nCopyright (c) 2017 Facebook Inc. All rights reserved.",
            reflow("Copyright 2019 Google LLC\nCopyright (c) 2017 Facebook Inc.\nAll rights reserved."),
        )
    }
}
