package se.birdy.pdf

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Kontraktstest för [JournalPdfRenderer] (iOS-actualen): renderar en RIKTIG PDF via
 * UIGraphicsPDFRenderer och verifierar sidantalet. Fixturbygget speglar
 * JournalPageAggregatorTest (commonTest, samma modul) — se dess `sampleInput`/`obsFor`.
 *
 * pageCount-facit (4) speglar Android-räkningen i JournalPdfRenderer.android.kt: titel(1) +
 * stats(2) + 1 artsida(3) + colophon(4) — inga badges → ingen badge-sida, ingen extra sida
 * efter colophon (se JournalPdfRenderer.ios.kts klass-KDoc för `pageNum`-bokföringen).
 */
class JournalPdfRendererIosTest {
    @Test
    fun renders_real_pdf_with_expected_page_count() =
        runTest {
            val captured = Instant.fromEpochMilliseconds(1_716_000_000_000L)
            val observation =
                Observation(
                    id = "obs-Q1-1",
                    speciesId = "Q1",
                    capturedAt = captured,
                    savedAt = captured,
                    photoPath = "/cache/p1.jpg",
                    note = "",
                    confidence = 0.9f,
                    latitude = null,
                    longitude = null,
                    locationLabel = null,
                    stampNumber = 1,
                    sourceType = ObservationSource.Photo,
                )
            val species =
                Species(
                    id = SpeciesId("Q1"),
                    scientificName = "Scientific Q1",
                    taxonomy = SpeciesTaxonomy(family = "F", familySv = "F", genus = "G", iocOrder = "0"),
                    name = "Art-Q1",
                    abundance = Abundance.ALLMÄN,
                    iucnStatus = "LC",
                    regions = listOf("EU"),
                    season = emptyMap(),
                    description = null,
                    migration = null,
                    images = emptyList(),
                )
            val input =
                JournalPdfInput(
                    displayName = "Albin",
                    generatedAtMs = 1_716_220_800_000L,
                    observations = listOf(observation),
                    speciesByQid = mapOf("Q1" to species),
                    stats =
                        JournalPdfInput.Stats(
                            speciesSeenThisYear = 1,
                            totalObservationsThisYear = 1,
                            topSpecies = listOf("Art-Q1" to 1),
                        ),
                    unlockedPremiumBadges = emptyList(),
                )
            val path = NSTemporaryDirectory() + "i4_test_${Random.nextInt(100000)}.pdf"
            val result = JournalPdfRenderer().render(input, path)
            // titel + stats + 1 artsida + colophon = 4 (inga badges)
            assertTrue(result is JournalPdfRenderResult.Success, "got: $result")
            assertEquals(4, (result as JournalPdfRenderResult.Success).pageCount)
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path))
            assertTrue(result.sizeBytes > 0)
        }
}
