package se.birdy.pdf

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationSource
import java.io.File
import kotlin.test.assertTrue

/**
 * Robolectric coverage for [JournalPdfRenderer] is intentionally narrow: `PdfDocument` has no
 * JVM/Robolectric backend (the AOSP class delegates to a native Skia PDF writer that is not
 * available in Robolectric's `nativeruntime`), so the rendered-PDF path is verified on a real
 * device in Plan 6b3 T21. Here we just exercise the early-return branches and font-asset wiring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JournalPdfRendererAndroidTest {
    @Test
    fun font_provider_loads_typefaces_from_assets() {
        val ctx = RuntimeEnvironment.getApplication()
        PdfFontProvider.init(ctx)
        // Throws if init didn't populate the typefaces.
        PdfFontProvider.dmSerifItalic()
        PdfFontProvider.caveat()
    }

    @Test
    fun render_empty_observations_returns_empty_without_writing_file() =
        runBlocking {
            val ctx = RuntimeEnvironment.getApplication()
            PdfFontProvider.init(ctx)
            val outFile = File(ctx.cacheDir, "journal_empty.pdf")
            if (outFile.exists()) outFile.delete()
            val renderer = JournalPdfRenderer()
            val result = renderer.render(emptyInput(), outFile.absolutePath)
            assertTrue(result is JournalPdfRenderResult.Empty, "got $result")
            assertTrue(!outFile.exists() || outFile.length() == 0L, "no file should be written")
        }

    private fun emptyInput(): JournalPdfInput =
        JournalPdfInput(
            displayName = "Albin",
            generatedAtMs = 1_716_220_800_000L,
            observations = emptyList(),
            speciesByQid = emptyMap(),
            stats = JournalPdfInput.Stats(0, 0, emptyList()),
            unlockedPremiumBadges = emptyList(),
        )

    @Suppress("unused")
    private fun sampleObservation(): Observation =
        Observation(
            id = "obs-1",
            speciesId = "Q25341",
            capturedAt = Instant.fromEpochMilliseconds(1_716_220_800_000L),
            savedAt = Instant.fromEpochMilliseconds(1_716_220_800_000L),
            photoPath = "/cache/p1.jpg",
            note = "",
            confidence = 0.91f,
            latitude = null,
            longitude = null,
            locationLabel = null,
            stampNumber = 1,
            sourceType = ObservationSource.Photo,
        )
}
