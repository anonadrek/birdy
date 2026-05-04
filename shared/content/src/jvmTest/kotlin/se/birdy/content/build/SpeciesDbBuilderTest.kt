package se.birdy.content.build

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import se.birdy.content.db.BirdyContent
import java.nio.file.Path

class SpeciesDbBuilderTest {
    private val parser = SpeciesYamlParser()

    @Test
    fun `produces a queryable sqlite file with one species`(
        @TempDir tempDir: Path,
    ) {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val outDb = tempDir.resolve("species.db")
        val outImages = tempDir.resolve("images")

        val builder = SpeciesDbBuilder()
        builder.build(
            items = items,
            sourceImageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            targetDb = outDb,
            targetImageRoot = outImages,
        )

        assertTrue(outDb.toFile().exists(), "species.db not created")
        assertTrue(outDb.toFile().length() > 0, "species.db empty")

        val driver = JdbcSqliteDriver("jdbc:sqlite:${outDb.toAbsolutePath()}")
        val db = BirdyContent(driver)
        val count = db.speciesQueries.count().executeAsOne()
        assertEquals(items.size.toLong(), count)

        val talgoxe = db.speciesQueries.selectById("Q25485").executeAsOneOrNull()
        assertEquals("Parus major", talgoxe?.scientific_name)

        val sv = db.speciesNameQueries.selectBySpecies("Q25485").executeAsList()
        assertTrue(sv.any { it.locale == "sv" && it.name == "Talgoxe" })

        driver.close()
    }
}
