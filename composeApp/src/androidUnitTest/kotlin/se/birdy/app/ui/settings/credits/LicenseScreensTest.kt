package se.birdy.app.ui.settings.credits

import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
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
    fun `a very long paragraph is laid out in pieces cut at line ends`() {
        val files = (1..400).joinToString("\n") { "./Eigen/src/Core/File$it.h" }
        val pieces = licenseParagraphs("Following applies to:\n$files\n\nNext.")
        assertTrue(pieces.size > 3, "${pieces.size} pieces")
        assertTrue(pieces.all { it.length <= PARAGRAPH_CHUNK_CHARS })
        assertEquals("Next.", pieces.last())
        assertEquals("Following applies to:\n$files", pieces.dropLast(1).joinToString("\n"))
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

    /**
     * QA 2026-10-07: the Apache License's definitions and the GPL's sub-items are indented, and each
     * of their 80-column lines kept its own line, so they wrapped raggedly on a phone. An indented
     * line that only continues the one before is joined; list items, copyright lines, short lines
     * and a line less indented than the one before keep their own line.
     */
    @Test
    fun `indented paragraphs wrapped for a narrow file are joined`() {
        assertEquals(
            "      \"License\" shall mean the terms and conditions for use, reproduction, " +
                "and distribution as defined by Sections 1 through 9 of this document.",
            reflow(
                "      \"License\" shall mean the terms and conditions for use, reproduction,\n" +
                    "      and distribution as defined by Sections 1 through 9 of this document.",
            ),
        )
        // A hanging indent: the item's number, then its text indented under it.
        assertEquals(
            "   2. Grant of Copyright License. Subject to the terms and conditions of " +
                "this License, each Contributor hereby grants to You a perpetual.",
            reflow(
                "   2. Grant of Copyright License. Subject to the terms and conditions of\n" +
                    "      this License, each Contributor hereby grants to You a perpetual.",
            ),
        )
        assertEquals(
            "    * Redistributions of source code must retain the above copyright notice, this list.\n" +
                "    * Redistributions in binary form must reproduce the above copyright notice.",
            reflow(
                "    * Redistributions of source code must retain the above copyright\n" +
                    "      notice, this list.\n" +
                    "    * Redistributions in binary form must reproduce the above copyright notice.",
            ),
        )
        assertEquals(
            "    Copyright 2010 The Android Open Source Project, all rights reserved here\n" +
                "    Copyright 2011 Google LLC",
            reflow(
                "    Copyright 2010 The Android Open Source Project, all rights reserved here\n" +
                    "    Copyright 2011 Google LLC",
            ),
        )
        // Centred title lines: each less indented than the one before.
        val title = "                                 Apache License\n                           Version 2.0, January 2004"
        assertEquals(title, reflow(title))
    }

    /**
     * The desugar libraries' texts start with lines Birdy writes itself (tools/licenses/generate.py):
     * the page shows them from strings, in the app's language. The English strings are the file's
     * lines word for word, so a new text from the generator fails here until the strings follow.
     */
    @Test
    @Config(qualifiers = "+en")
    fun `the english preface strings are the file's own lines`() {
        attachComposeResourcesContext()
        for ((file, preface) in LICENSE_PREFACES) {
            val lines = runBlocking { LicenseFiles.read(file) }.replace("\r\n", "\n").split('\n')
            assertEquals(lines.take(preface.size), preface.map { runBlocking { getString(it) } }, file)
            assertTrue(lines[preface.size].isBlank(), "$file: the preface is a paragraph of its own")
        }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the swedish page shows birdy's own lines in swedish and the licence as written`() {
        attachComposeResourcesContext()
        val text = runBlocking { loadLicenseText("lib:com.android.tools:2.1.5:desugar_jdk_libs") }
        assertTrue(text.paragraphs.none { "Source code:" in it || "Source of this text:" in it })
        assertEquals("The GNU General Public License (GPL)", text.paragraphs.first())
        compose.setContent { BirdyTheme { LicenseTextScreen(state = Loadable.Loaded(text), onBack = {}) } }
        compose.onNodeWithText("Java-bibliotekskod från OpenJDK", substring = true).assertExists()
        compose.onNodeWithText("Källkod: https://github.com/google/desugar_jdk_libs/tree/", substring = true).assertExists()
        compose.onNodeWithText("Källa till texten: https://raw.githubusercontent.com/", substring = true).assertExists()
    }
}
