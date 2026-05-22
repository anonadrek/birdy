package se.birdy.pdf

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
}
