package se.birdy.content

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import se.birdy.content.build.SpeciesDbBuilder
import se.birdy.content.build.SpeciesYamlParser
import se.birdy.content.db.BirdyContent
import se.birdy.content.search.normalizeSearch
import java.nio.file.Path

class SpeciesRepositoryTest {
    private val parser = SpeciesYamlParser()

    private fun newDriverWithFixtures(tempDir: Path): JdbcSqliteDriver {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val outDb = tempDir.resolve("species.db")
        SpeciesDbBuilder().build(
            items = items,
            sourceImageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            targetDb = outDb,
            targetImageRoot = tempDir.resolve("images"),
        )
        return JdbcSqliteDriver("jdbc:sqlite:${outDb.toAbsolutePath()}")
    }

    @Test
    fun `get by id returns species in requested locale`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

        val sv = repo.getById(SpeciesId("Q25485"), Locale.SV).first()
        assertNotNull(sv)
        assertEquals("Talgoxe", sv?.name)
        assertTrue(sv?.description?.contains("Talgoxen") == true)

        val en = repo.getById(SpeciesId("Q25485"), Locale.EN).first()
        assertEquals("Great Tit", en?.name)

        driver.close()
    }

    @Test
    fun `search by name returns matches`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        val results =
            repo.search(query = "Talg", locale = Locale.SV, filters = SpeciesFilter()).first()
        assertTrue(results.any { it.id == SpeciesId("Q25485") })
        driver.close()
    }

    @Test
    fun `listByFamily returns all paridae`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        val results = repo.listByFamily("Paridae", Locale.SV).first()
        assertTrue(results.any { it.id == SpeciesId("Q25485") })
        driver.close()
    }

    @Test
    fun `i18n fallback uses english when swedish missing`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val db = BirdyContent(driver)
        // remove sv text + sv name to force fallback
        driver.execute(null, "DELETE FROM SpeciesText WHERE locale = 'sv' AND species_id = 'Q25485'", 0)
        driver.execute(null, "DELETE FROM SpeciesName WHERE locale = 'sv' AND species_id = 'Q25485'", 0)
        val repo = SqlDelightSpeciesRepository(db)
        val sv = repo.getById(SpeciesId("Q25485"), Locale.SV).first()
        assertNotNull(sv)
        assertEquals("Great Tit", sv?.name)
        assertTrue(sv?.description?.contains("Great Tit") == true)
        driver.close()
    }

    @Test
    fun `search matches scientific name`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        val results = repo.search(query = "Parus", locale = Locale.SV, filters = SpeciesFilter()).first()
        assertTrue(
            results.any { it.id == SpeciesId("Q25485") },
            "expected to find Talgoxe (Parus major) when searching 'Parus', got ${results.map { it.scientificName }}",
        )
        driver.close()
    }

    @Test
    fun `search filter by region restricts results`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        val results =
            repo
                .search(
                    query = "Talg",
                    locale = Locale.SV,
                    filters = SpeciesFilter(regions = setOf("SE")),
                ).first()
        assertTrue(results.any { it.id == SpeciesId("Q25485") })

        val noResults =
            repo
                .search(
                    query = "Talg",
                    locale = Locale.SV,
                    filters = SpeciesFilter(regions = setOf("ZZ")),
                ).first()
        assertTrue(noResults.none { it.id == SpeciesId("Q25485") })

        driver.close()
    }

    @Test
    fun `search filter by activeInMonth restricts results`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        val janResults =
            repo
                .search(
                    query = "Talg",
                    locale = Locale.SV,
                    filters = SpeciesFilter(activeInMonth = "jan"),
                ).first()
        assertTrue(janResults.any { it.id == SpeciesId("Q25485") })

        driver.execute(null, "DELETE FROM SpeciesSeason WHERE species_id = 'Q25485' AND month = 'jan'", 0)
        val janResultsAfter =
            repo
                .search(
                    query = "Talg",
                    locale = Locale.SV,
                    filters = SpeciesFilter(activeInMonth = "jan"),
                ).first()
        assertTrue(janResultsAfter.none { it.id == SpeciesId("Q25485") })

        driver.close()
    }

    @Test
    fun `search empty query returns all species respecting filters`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        val all = repo.search(query = "", locale = Locale.SV, filters = SpeciesFilter()).first()
        assertTrue(all.any { it.id == SpeciesId("Q25485") }, "expected fixture species in empty-query result, got ${all.map { it.id }}")
        driver.close()
    }

    @Test
    fun `observeTotalCount returns species count`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        val count = repo.observeTotalCount().first()
        assertEquals(1, count)
        driver.close()
    }

    @Test
    fun `allByQid returns map keyed by SpeciesId with full species data`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        val map = repo.allByQid(Locale.SV)
        assertEquals(1, map.size)
        val talgoxe = map[SpeciesId("Q25485")]
        assertNotNull(talgoxe)
        assertEquals("Talgoxe", talgoxe?.name)
        assertEquals("Paridae", talgoxe?.taxonomy?.family)
        assertEquals(Abundance.ALLMÄN, talgoxe?.abundance)
        driver.close()
    }

    @Test
    fun `allByQid returns localized names per requested locale`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

        val sv = repo.allByQid(Locale.SV)
        assertEquals("Talgoxe", sv[SpeciesId("Q25485")]?.name)

        val en = repo.allByQid(Locale.EN)
        assertEquals("Great Tit", en[SpeciesId("Q25485")]?.name)

        driver.close()
    }

    // --- In-memory helpers for cross-locale / normalization regression tests ---

    private fun newInMemoryDb(): BirdyContent {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BirdyContent.Schema.create(driver)
        return BirdyContent(driver)
    }

    /**
     * Seeds Species + SpeciesTaxonomy + both SpeciesName rows (with normalized search_text).
     * Column order mirrors the generated insert() queries in the .sq files.
     */
    private fun BirdyContent.seedSpecies(
        id: String,
        sci: String,
        family: String,
        familySv: String,
        genus: String,
        sv: String,
        en: String,
        abundance: String = "ovanlig",
    ) {
        speciesQueries.insert(id, sci, abundance, "LC", "2026-01-01", "approved", null, null, null)
        speciesTaxonomyQueries.insert(id, family, familySv, genus, "Falconiformes", "other")
        speciesNameQueries.insert(
            id,
            "sv",
            sv,
            normalizeSearch("$sv $sci $family $familySv $genus"),
        )
        speciesNameQueries.insert(
            id,
            "en",
            en,
            normalizeSearch("$en $sci $family $familySv $genus"),
        )
    }

    @Test
    fun `finds Eleonora with plain apostrophe against U2019 data`() =
        runTest {
            // EN name stored with U+2019 RIGHT SINGLE QUOTATION MARK: "Eleonora’s Falcon"
            val db = newInMemoryDb()
            db.seedSpecies(
                "Q212243",
                "Falco eleonorae",
                "Falconidae",
                "falkfåglar",
                "Falco",
                sv = "Eleonorafalk",
                en = "Eleonora’s Falcon", // U+2019, as stored in YAML data
            )
            val repo = SqlDelightSpeciesRepository(db)
            // Query with plain ASCII apostrophe — normalizeSearch strips both to same token
            val hits = repo.search("Eleonora's Falcon", Locale.EN, SpeciesFilter()).first()
            assertEquals(1, hits.size)
            assertEquals("Q212243", hits.first().id.raw)
        }

    @Test
    fun `cross-locale finds english name while in SV locale`() =
        runTest {
            val db = newInMemoryDb()
            db.seedSpecies(
                "Q212243",
                "Falco eleonorae",
                "Falconidae",
                "falkfåglar",
                "Falco",
                sv = "Eleonorafalk",
                en = "Eleonora’s Falcon",
            )
            val repo = SqlDelightSpeciesRepository(db)
            // Query uses plain ASCII apostrophe (exercises normalization too) and matches the
            // EN name cross-locale — result should still show the SV display name.
            val hits = repo.search("Eleonora's Falcon", Locale.SV, SpeciesFilter()).first()
            assertEquals(1, hits.size)
            assertEquals("Eleonorafalk", hits.first().name) // display name in user's (SV) locale
        }

    @Test
    fun `diacritic-insensitive`() =
        runTest {
            val db = newInMemoryDb()
            db.seedSpecies(
                "Q1",
                "Gyps rueppellii",
                "Accipitridae",
                "hökartade rovfåglar",
                "Gyps",
                sv = "Rüppellgam",
                en = "Rüppell’s Vulture",
            )
            val repo = SqlDelightSpeciesRepository(db)
            assertEquals(1, repo.search("ruppell", Locale.EN, SpeciesFilter()).first().size)
        }

    @Test
    fun `search carries ecological group and family_sv`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = newDriverWithFixtures(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

        val results = repo.search(query = "Talg", locale = Locale.SV, filters = SpeciesFilter()).first()
        val talgoxe = results.first { it.id == SpeciesId("Q25485") }
        assertEquals("songbirds", talgoxe.group)
        assertEquals("Mesar", talgoxe.familySv) // fixturens family_sv för Paridae

        driver.close()
    }

    @Test
    fun `family search returns family members`() =
        runTest {
            val db = newInMemoryDb()
            db.seedSpecies(
                "Q212243",
                "Falco eleonorae",
                "Falconidae",
                "falkfåglar",
                "Falco",
                sv = "Eleonorafalk",
                en = "Eleonora’s Falcon",
            )
            val repo = SqlDelightSpeciesRepository(db)
            // "falconidae" appears ONLY in the family token of the blob (not in the display
            // name "Eleonora's Falcon" nor the scientific name "Falco eleonorae") — so this
            // genuinely isolates family-blob matching.
            assertEquals(1, repo.search("falconidae", Locale.EN, SpeciesFilter()).first().size)
        }
}
