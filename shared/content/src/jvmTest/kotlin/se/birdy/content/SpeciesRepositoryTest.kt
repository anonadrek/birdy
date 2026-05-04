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
}
