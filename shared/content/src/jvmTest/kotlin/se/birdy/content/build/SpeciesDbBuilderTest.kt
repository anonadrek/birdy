package se.birdy.content.build

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import se.birdy.content.db.BirdyContent
import java.nio.file.Files
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
    fun `fingerprint changes when a text is edited by hand without touching generated_at`() {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val builder = SpeciesDbBuilder()
        val edited =
            items.map { (path, yaml) ->
                path to yaml.copy(description = yaml.description + ("sv" to "Handskriven text."))
            }
        assertNotEquals(
            builder.contentFingerprint(items, 2),
            builder.contentFingerprint(edited, 2),
            "a hand edit that keeps generated_at must still flip application_id, or installed apps keep the old db",
        )
    }

    @Test
    fun `fingerprint changes when a photo or its credit is replaced`() {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val builder = SpeciesDbBuilder()
        val newCredit =
            items.map { (path, yaml) ->
                path to yaml.copy(image_refs = yaml.image_refs.map { it.copy(author = "Charles J. Sharp") })
            }
        val newPhoto =
            items.map { (path, yaml) ->
                path to yaml.copy(image_refs = yaml.image_refs.map { it.copy(commons_filename = "Other.jpg") })
            }
        val before = builder.contentFingerprint(items, 2)
        assertNotEquals(before, builder.contentFingerprint(newCredit, 2), "a cleaned credit must reach installed apps")
        assertNotEquals(before, builder.contentFingerprint(newPhoto, 2), "a replaced photo must reach installed apps")
    }

    @Test
    fun `fingerprint is unaffected by CRLF line endings in the source yaml file`(
        @TempDir tempDir: Path,
    ) {
        val items = parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val builder = SpeciesDbBuilder()

        // T11f/F2: a hand-edited YAML checked out with Windows line endings must fingerprint the
        // same as the LF original — kaml normalizes \r\n in scalars, but this locks that in at the
        // file level instead of trusting the parser's behaviour by inspection only.
        val sourceDir = Path.of("src/jvmTest/resources/fixtures/species")
        val crlfDir = tempDir.resolve("species-crlf")
        Files.walk(sourceDir).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { file ->
                val target = crlfDir.resolve(sourceDir.relativize(file))
                Files.createDirectories(target.parent)
                val crlfContent = Files.readString(file).replace("\r\n", "\n").replace("\n", "\r\n")
                Files.writeString(target, crlfContent)
            }
        }
        val crlfItems = parser.parseAll(crlfDir)

        assertEquals(
            builder.contentFingerprint(items, 2),
            builder.contentFingerprint(crlfItems, 2),
            "a CRLF checkout of the same YAML content must produce the same fingerprint",
        )
    }

    @Test
    fun `fingerprint is unaffected by item order`() {
        val items = parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val builder = SpeciesDbBuilder()
        val (path, yaml) = items.first()
        val threeItems =
            listOf(
                path to yaml.copy(id = "Q100001"),
                path to yaml.copy(id = "Q100002"),
                path to yaml.copy(id = "Q100003"),
            )

        assertEquals(
            builder.contentFingerprint(threeItems, 2),
            builder.contentFingerprint(threeItems.reversed(), 2),
            "a reversed item order must not change the fingerprint",
        )
        assertEquals(
            builder.contentFingerprint(threeItems, 2),
            builder.contentFingerprint(threeItems.shuffled(), 2),
            "a shuffled item order must not change the fingerprint",
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

    @Test
    fun `stamps ecological group_id on taxonomy`(
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

        val driver = JdbcSqliteDriver("jdbc:sqlite:${outDb.toAbsolutePath()}")
        val db = BirdyContent(driver)
        // Fixturen Q25485 = Paridae / Passeriformes → songbirds.
        val taxonomy = db.speciesTaxonomyQueries.selectBySpecies("Q25485").executeAsOne()
        assertEquals("songbirds", taxonomy.group_id)
        driver.close()
    }

    @Test
    fun `removes images from the target folder that no species references any more`(
        @TempDir tempDir: Path,
    ) {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val outImages = tempDir.resolve("images")
        val dropped = outImages.resolve("Q11111/hero.webp")
        val droppedSecondary = outImages.resolve("Q25485/secondary-2.webp")
        Files.createDirectories(dropped.parent)
        Files.createDirectories(droppedSecondary.parent)
        Files.writeString(dropped, "old range map")
        Files.writeString(droppedSecondary, "old egg photo")

        SpeciesDbBuilder().build(
            items = items,
            sourceImageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            targetDb = tempDir.resolve("species.db"),
            targetImageRoot = outImages,
        )

        assertTrue(Files.exists(outImages.resolve("Q25485/hero.jpg")), "referenced image must be copied")
        assertFalse(Files.exists(droppedSecondary), "unreferenced secondary must be removed")
        assertFalse(Files.exists(dropped), "unreferenced image must be removed")
        assertFalse(Files.exists(dropped.parent), "empty species folder must be removed")
    }

    @Test
    fun `leaves finder and explorer files in the target folder alone`(
        @TempDir tempDir: Path,
    ) {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val outImages = tempDir.resolve("images")
        val junk = listOf(".DS_Store", "Q25485/.DS_Store", "Q25485/Thumbs.db").map { outImages.resolve(it) }
        for (file in junk) {
            Files.createDirectories(file.parent)
            Files.writeString(file, "x")
        }

        SpeciesDbBuilder().build(
            items = items,
            sourceImageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            targetDb = tempDir.resolve("species.db"),
            targetImageRoot = outImages,
        )

        assertTrue(Files.exists(outImages.resolve("Q25485/hero.jpg")))
        for (file in junk) assertTrue(Files.exists(file), "$file must not be pruned")
    }

    private fun readApplicationId(db: Path): Int {
        val header = db.toFile().inputStream().use { it.readNBytes(100) }
        return ((header[68].toInt() and 0xFF) shl 24) or
            ((header[69].toInt() and 0xFF) shl 16) or
            ((header[70].toInt() and 0xFF) shl 8) or
            (header[71].toInt() and 0xFF)
    }
}
