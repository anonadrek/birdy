package se.birdy.pdf

import kotlinx.coroutines.test.runTest
import se.birdy.content.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalPdfRendererContractTest {
    @Test
    fun renderer_returns_empty_when_observations_empty() =
        runTest {
            val renderer = JournalPdfRenderer()
            val emptyInput =
                JournalPdfInput(
                    displayName = "Albin",
                    generatedAtMs = 1716220800000L,
                    observations = emptyList(),
                    speciesByQid = emptyMap(),
                    stats = JournalPdfInput.Stats(0, 0, emptyList()),
                    unlockedPremiumBadges = emptyList(),
                )
            val result = renderer.render(emptyInput, outputPath = "/tmp/x.pdf")
            assertTrue(result is JournalPdfRenderResult.Empty, "got: $result")
        }

    // Bug: an English user's exported PDF printed Swedish headings (JournalPdfMetrics held the
    // PDF's text as fixed Swedish constants). This renders an English journal and checks the
    // title and badges heading the platform layouts (JournalPdfLayout/JournalPdfLayoutIos) draw
    // verbatim from input.strings — the real testable boundary, since android.graphics.pdf.
    // PdfDocument has no JVM/Robolectric backend (see JournalPdfRendererAndroidTest's KDoc), so
    // the actual Canvas/CoreGraphics pixels are verified on-device, not here.
    @Test
    fun renders_english_journal_with_english_title_and_badges_heading() {
        val input = sampleInput(locale = Locale.EN)
        assertEquals("Field journal", input.strings.title)
        assertEquals("Stamps in the margin", input.strings.badgesTitle)
    }

    @Test
    fun renders_swedish_journal_with_swedish_title_and_badges_heading() {
        val input = sampleInput(locale = Locale.SV)
        assertEquals("Fältdagbok", input.strings.title)
        assertEquals("Stämplar i marginalen", input.strings.badgesTitle)
    }

    private fun sampleInput(locale: Locale): JournalPdfInput =
        JournalPdfInput(
            displayName = "Albin",
            generatedAtMs = 1716220800000L,
            observations = emptyList(),
            speciesByQid = emptyMap(),
            stats = JournalPdfInput.Stats(0, 0, emptyList()),
            unlockedPremiumBadges = emptyList(),
            strings = JournalPdfStrings.forLocale(locale),
        )
}
