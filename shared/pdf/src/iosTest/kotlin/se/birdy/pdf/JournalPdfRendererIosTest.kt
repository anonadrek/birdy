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
            val input = sampleInput(strings = JournalPdfStrings.SV, badges = emptyList())
            val path = NSTemporaryDirectory() + "i4_test_${Random.nextInt(100000)}.pdf"
            val result = JournalPdfRenderer().render(input, path)
            // titel + stats + 1 artsida + colophon = 4 (inga badges)
            assertTrue(result is JournalPdfRenderResult.Success, "got: $result")
            assertEquals(4, (result as JournalPdfRenderResult.Success).pageCount)
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path))
            assertTrue(result.sizeBytes > 0)
        }

    // Bugg: en engelsk användares export fick svenska rubriker (texten låg som fasta svenska
    // konstanter på JournalPdfMetrics). Renderar en RIKTIG engelsk PDF med ett upplåst märke, så
    // att även märkessidan — vars rubrik var en av de svenska — faktiskt ritas ur
    // JournalPdfStrings.EN. Själva texten i PDF:en kontrolleras inte (CoreGraphics skriver
    // glyf-id:n i komprimerade innehållsströmmar, rubrikerna går inte att greppa ur filen); det
    // här bevisar att den engelska uppsättningen ritar varje sida utan fel, och vilka rubriker
    // som ritades står i asserterna på input.strings. Androids pixlar kontrolleras på enhet.
    @Test
    fun renders_real_english_pdf_including_badges_page() =
        runTest {
            val badge =
                JournalPdfInput.BadgeRef(
                    id = "premium_field_member",
                    nameLocalized = "Field member",
                    descriptionLocalized = "Joined Birdy Premium.",
                    unlockedAt = Instant.fromEpochMilliseconds(1_716_000_000_000L),
                )
            val input = sampleInput(strings = JournalPdfStrings.EN, badges = listOf(badge))
            assertEquals("Field journal", input.strings.title)
            assertEquals("Stamps in the margin", input.strings.badgesTitle)
            val path = NSTemporaryDirectory() + "i4_test_en_${Random.nextInt(100000)}.pdf"
            val result = JournalPdfRenderer().render(input, path)
            // titel + stats + 1 artsida + märken + colophon = 5
            assertTrue(result is JournalPdfRenderResult.Success, "got: $result")
            assertEquals(5, (result as JournalPdfRenderResult.Success).pageCount)
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path))
            assertTrue(result.sizeBytes > 0)
        }

    // Release 1.3.1 del 8 (fixomgång): ett artnamn och ett vetenskapligt namn på ~60 tecken samt
    // en märkesbeskrivning på ~120 tecken tvingar BÅDE krymp- och kortningsvägen i [fitLabel] —
    // genom den riktiga CoreGraphics-renderaren (macOS CI), inte bara commonMain-matematiken.
    @Test
    fun renders_real_pdf_with_long_labels_that_shrink_and_cut() =
        runTest {
            val longName = "The Great Spotted Long-Named Testing Woodpecker Species Q1"
            val longSci = "Testus scientificus verylongus nominus exampleus speciesia"
            val longDesc =
                "This premium field badge has an unusually long description, written to test the PDF label-fitting logic end to end on iOS"
            val badge =
                JournalPdfInput.BadgeRef(
                    id = "premium_field_member",
                    nameLocalized = "Field member",
                    descriptionLocalized = longDesc,
                    unlockedAt = Instant.fromEpochMilliseconds(1_716_000_000_000L),
                )
            val input =
                sampleInput(
                    strings = JournalPdfStrings.EN,
                    badges = listOf(badge),
                    speciesName = longName,
                    scientificName = longSci,
                )
            val path = NSTemporaryDirectory() + "i4_test_long_${Random.nextInt(100000)}.pdf"
            val result = JournalPdfRenderer().render(input, path)
            // titel + stats + 1 artsida + märken + colophon = 5
            assertTrue(result is JournalPdfRenderResult.Success, "got: $result")
            assertEquals(5, (result as JournalPdfRenderResult.Success).pageCount)
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path))
            assertTrue(result.sizeBytes > 0)
        }

    private fun sampleInput(
        strings: JournalPdfStrings,
        badges: List<JournalPdfInput.BadgeRef>,
        speciesName: String = "Art-Q1",
        scientificName: String = "Scientific Q1",
    ): JournalPdfInput {
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
                scientificName = scientificName,
                taxonomy = SpeciesTaxonomy(family = "F", familySv = "F", genus = "G", iocOrder = "0"),
                name = speciesName,
                abundance = Abundance.ALLMÄN,
                iucnStatus = "LC",
                regions = listOf("EU"),
                season = emptyMap(),
                description = null,
                migration = null,
                images = emptyList(),
            )
        return JournalPdfInput(
            displayName = "Albin",
            generatedAtMs = 1_716_220_800_000L,
            observations = listOf(observation),
            speciesByQid = mapOf("Q1" to species),
            stats =
                JournalPdfInput.Stats(
                    speciesSeenThisYear = 1,
                    totalObservationsThisYear = 1,
                    topSpecies = listOf(speciesName to 1),
                ),
            unlockedPremiumBadges = badges,
            strings = strings,
        )
    }
}
