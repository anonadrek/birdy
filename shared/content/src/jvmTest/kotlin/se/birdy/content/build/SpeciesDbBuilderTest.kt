package se.birdy.content.build

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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

    @Test
    fun `stamps a non-zero application_id derived from content`(
        @TempDir tempDir: Path,
    ) {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val outDb = tempDir.resolve("species.db")

        SpeciesDbBuilder().build(
            items = items,
            sourceImageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            targetDb = outDb,
            targetImageRoot = tempDir.resolve("images"),
        )

        assertNotEquals(0, readApplicationId(outDb), "application_id should be content-derived, not zero")
    }

    @Test
    fun `application_id is stable for identical content but changes when generated_at changes`(
        @TempDir tempDir: Path,
    ) {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val builder = SpeciesDbBuilder()

        val dbA = tempDir.resolve("a.db")
        builder.build(items, Path.of("src/jvmTest/resources/fixtures/images"), dbA, tempDir.resolve("imgA"))

        val dbB = tempDir.resolve("b.db")
        builder.build(items, Path.of("src/jvmTest/resources/fixtures/images"), dbB, tempDir.resolve("imgB"))

        assertEquals(readApplicationId(dbA), readApplicationId(dbB), "same input → same application_id")

        val mutated =
            items.map { (path, yaml) ->
                path to yaml.copy(generated_at = yaml.generated_at + "X")
            }
        val dbC = tempDir.resolve("c.db")
        builder.build(mutated, Path.of("src/jvmTest/resources/fixtures/images"), dbC, tempDir.resolve("imgC"))

        assertNotEquals(readApplicationId(dbA), readApplicationId(dbC), "changed generated_at → new application_id")
    }

    @Test
    fun `schema rev change flips fingerprint`() {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val builder = SpeciesDbBuilder()
        assertNotEquals(
            builder.contentFingerprint(items, 1),
            builder.contentFingerprint(items, 2),
        )
    }

    @Test
    fun `fingerprint stable for same content and schema rev`() {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val builder = SpeciesDbBuilder()
        assertEquals(
            builder.contentFingerprint(items, 2),
            builder.contentFingerprint(items, 2),
        )
    }

    private fun readApplicationId(db: Path): Int {
        val header = db.toFile().inputStream().use { it.readNBytes(100) }
        return ((header[68].toInt() and 0xFF) shl 24) or
            ((header[69].toInt() and 0xFF) shl 16) or
            ((header[70].toInt() and 0xFF) shl 8) or
            (header[71].toInt() and 0xFF)
    }
}
