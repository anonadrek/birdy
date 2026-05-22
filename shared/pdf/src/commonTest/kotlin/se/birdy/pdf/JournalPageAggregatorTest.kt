package se.birdy.pdf

import kotlinx.datetime.Instant
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalPageAggregatorTest {
    @Test
    fun groups_observations_by_species_and_sorts_by_count_desc() {
        val input =
            sampleInput(
                obsCounts = mapOf("Q1" to 1, "Q2" to 3, "Q3" to 2),
            )
        val pages = JournalPageAggregator.computeSpeciesPages(input)
        assertEquals(1, pages.size)
        val rows = pages[0]
        assertEquals(listOf("Q2", "Q3", "Q1"), rows.map { it.qid })
        assertEquals(3, rows[0].count)
        assertEquals(2, rows[1].count)
        assertEquals(1, rows[2].count)
    }

    @Test
    fun observations_without_species_are_filtered_out() {
        val obsWithNoSpecies =
            obsFor(qid = null, capturedMs = 1_716_000_000_000L, stamp = 99)
        val input =
            sampleInput(obsCounts = mapOf("Q1" to 2))
                .copy(observations = sampleInput(obsCounts = mapOf("Q1" to 2)).observations + obsWithNoSpecies)
        val pages = JournalPageAggregator.computeSpeciesPages(input)
        assertEquals(1, pages.size)
        assertEquals(listOf("Q1"), pages[0].map { it.qid })
    }

    @Test
    fun paginates_at_species_per_page_boundary() {
        val counts = (1..(JournalPageAggregator.SPECIES_PER_PAGE + 5)).associate { "Q$it" to 1 }
        val pages = JournalPageAggregator.computeSpeciesPages(sampleInput(counts))
        assertEquals(2, pages.size)
        assertEquals(JournalPageAggregator.SPECIES_PER_PAGE, pages[0].size)
        assertEquals(5, pages[1].size)
    }

    @Test
    fun truncates_at_max_species_total() {
        val counts = (1..(JournalPageAggregator.MAX_SPECIES_TOTAL + 10)).associate { "Q$it" to 1 }
        val pages = JournalPageAggregator.computeSpeciesPages(sampleInput(counts))
        val total = pages.sumOf { it.size }
        assertEquals(JournalPageAggregator.MAX_SPECIES_TOTAL, total)
    }

    @Test
    fun empty_observations_returns_empty_list() {
        val pages = JournalPageAggregator.computeSpeciesPages(sampleInput(emptyMap()))
        assertTrue(pages.isEmpty())
    }

    @Test
    fun first_seen_is_minimum_captured_at_per_species() {
        val obs1 = obsFor("Q1", capturedMs = 1_716_000_000_000L, stamp = 1)
        val obs2 = obsFor("Q1", capturedMs = 1_715_000_000_000L, stamp = 2)
        val obs3 = obsFor("Q1", capturedMs = 1_717_000_000_000L, stamp = 3)
        val input = sampleInput(emptyMap()).copy(observations = listOf(obs1, obs2, obs3))
        val pages = JournalPageAggregator.computeSpeciesPages(input)
        assertEquals(1_715_000_000_000L, pages[0][0].firstSeenMs)
    }

    @Test
    fun species_metadata_resolves_to_localized_name_and_scientific_name() {
        val input =
            sampleInput(emptyMap()).copy(
                observations = listOf(obsFor("Q25341", 1_716_000_000_000L, stamp = 1)),
                speciesByQid =
                    mapOf(
                        "Q25341" to fakeSpecies("Q25341", "Blåmes", "Cyanistes caeruleus"),
                    ),
            )
        val rows = JournalPageAggregator.computeSpeciesPages(input).single()
        assertEquals("Blåmes", rows.single().nameLocalized)
        assertEquals("Cyanistes caeruleus", rows.single().scientificName)
    }

    @Test
    fun unknown_species_id_falls_back_to_qid_and_empty_scientific_name() {
        val input =
            sampleInput(emptyMap()).copy(
                observations = listOf(obsFor("QNOPE", 1_716_000_000_000L, stamp = 1)),
            )
        val rows = JournalPageAggregator.computeSpeciesPages(input).single()
        assertEquals("QNOPE", rows.single().nameLocalized)
        assertEquals("", rows.single().scientificName)
    }

    // -- helpers --

    private fun obsFor(
        qid: String?,
        capturedMs: Long,
        stamp: Int,
    ): Observation {
        val captured = Instant.fromEpochMilliseconds(capturedMs)
        return Observation(
            id = "obs-${qid ?: "x"}-$stamp",
            speciesId = qid,
            capturedAt = captured,
            savedAt = captured,
            photoPath = "/cache/p$stamp.jpg",
            note = "",
            confidence = 0.9f,
            latitude = null,
            longitude = null,
            locationLabel = null,
            stampNumber = stamp,
            sourceType = ObservationSource.Photo,
        )
    }

    private fun sampleInput(obsCounts: Map<String, Int>): JournalPdfInput {
        var stamp = 0
        val obs =
            obsCounts.flatMap { (qid, count) ->
                List(count) { i ->
                    stamp += 1
                    obsFor(qid, capturedMs = 1_716_000_000_000L + i * 1_000L, stamp = stamp)
                }
            }
        val speciesByQid =
            obsCounts.keys.associateWith { qid ->
                fakeSpecies(qid, name = "Art-$qid", scientificName = "Scientific $qid")
            }
        return JournalPdfInput(
            displayName = "Albin",
            generatedAtMs = 1_716_220_800_000L,
            observations = obs,
            speciesByQid = speciesByQid,
            stats = JournalPdfInput.Stats(speciesByQid.size, obs.size, emptyList()),
            unlockedPremiumBadges = emptyList(),
        )
    }

    private fun fakeSpecies(
        id: String,
        name: String,
        scientificName: String,
    ): Species =
        Species(
            id = SpeciesId(id),
            scientificName = scientificName,
            taxonomy = SpeciesTaxonomy(family = "F", familySv = "F", genus = "G", iocOrder = "0"),
            name = name,
            abundance = Abundance.ALLMÄN,
            iucnStatus = "LC",
            regions = listOf("EU"),
            season = emptyMap(),
            description = null,
            migration = null,
            images = emptyList(),
        )
}
