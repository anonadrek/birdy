package se.birdy.content

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import se.birdy.content.build.SpeciesDbBuilder
import se.birdy.content.build.SpeciesYamlParser
import se.birdy.content.db.BirdyContent
import se.birdy.content.model.SpeciesTextSource
import java.nio.file.Files
import java.nio.file.Path

/**
 * Release 1.3.0, legal review §4, 7i-fix A: the species texts are AI summaries of the Wikipedia
 * article in the same language (CC BY-SA 4.0), so the profile credits the exact article version
 * behind the text it actually shows. The fixture talgoxe has Swedish and English texts written
 * from sv revision 12345678 and en revision 87654321.
 */
class SpeciesTextSourcesTest {
    private val talgoxe = SpeciesId("Q25485")
    private val sv = SpeciesTextSource(Locale.SV, "12345678", "https://sv.wikipedia.org/w/index.php?oldid=12345678")
    private val en = SpeciesTextSource(Locale.EN, "87654321", "https://en.wikipedia.org/w/index.php?oldid=87654321")

    private fun fixture(tempDir: Path): JdbcSqliteDriver {
        val items = SpeciesYamlParser().parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val outDb = tempDir.resolve("species.db")
        SpeciesDbBuilder().build(
            items = items,
            sourceImageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            targetDb = outDb,
            targetImageRoot = tempDir.resolve("images"),
        )
        return JdbcSqliteDriver("jdbc:sqlite:${outDb.toAbsolutePath()}")
    }

    private fun JdbcSqliteDriver.delete(where: String) = execute(null, "DELETE FROM SpeciesText WHERE species_id = 'Q25485' AND $where", 0)

    @Test
    fun `a text shown in the app's language credits that language's article version`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = fixture(tempDir)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

        assertEquals(listOf(sv), repo.getById(talgoxe, Locale.SV).first()?.textSources)
        assertEquals(listOf(en), repo.getById(talgoxe, Locale.EN).first()?.textSources)
        assertEquals(listOf(sv), repo.allByQid(Locale.SV).getValue(talgoxe).textSources)
        driver.close()
    }

    @Test
    fun `the english fallback credits the english article`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = fixture(tempDir)
        driver.delete("locale = 'sv'")
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

        val species = repo.getById(talgoxe, Locale.SV).first()
        assertEquals(true, species?.description?.contains("Great Tit"))
        assertEquals(listOf(en), species?.textSources)
        assertEquals(listOf(en), repo.allByQid(Locale.SV).getValue(talgoxe).textSources)
        driver.close()
    }

    // A Swedish description with the English migration text (the English fallback, per text).
    @Test
    fun `texts in two languages credit both articles, description first`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = fixture(tempDir)
        driver.delete("locale = 'sv' AND kind = 'migration'")
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

        assertEquals(listOf(sv, en), repo.getById(talgoxe, Locale.SV).first()?.textSources)
        driver.close()
    }

    // A text the cleaner empties (a "no data" sentinel) shows the app's own empty line, which is
    // not Wikipedia's: no credit for it.
    @Test
    fun `no text shown means no source`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = fixture(tempDir)
        driver.delete("kind IN ('description', 'migration')")
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

        val species = repo.getById(talgoxe, Locale.SV).first()
        assertEquals(null, species?.description)
        assertEquals(emptyList<SpeciesTextSource>(), species?.textSources)
        assertEquals(emptyList<SpeciesTextSource>(), repo.allByQid(Locale.EN).getValue(talgoxe).textSources)
        driver.close()
    }

    @Test
    fun `marginalia count as text`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = fixture(tempDir)
        driver.delete("kind IN ('description', 'migration')")
        driver.execute(null, "INSERT INTO SpeciesText VALUES ('Q25485', 'sv', 'marginalia', 'Söker frön i barren.')", 0)
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

        assertEquals(listOf(sv), repo.getById(talgoxe, Locale.SV).first()?.textSources)
        driver.close()
    }

    @Test
    fun `without a stored revision the credit links the species' article through wikidata`() {
        assertEquals(
            "https://www.wikidata.org/wiki/Special:GoToLinkedPage/enwiki/Q25485",
            WikipediaLinks.articleUrl(Locale.EN, null, talgoxe),
        )
        assertEquals(
            "https://www.wikidata.org/wiki/Special:GoToLinkedPage/svwiki/Q25485",
            WikipediaLinks.articleUrl(Locale.SV, "not-a-revision", talgoxe),
        )
        assertEquals("https://sv.wikipedia.org/w/index.php?oldid=59064377", WikipediaLinks.articleUrl(Locale.SV, "59064377", talgoxe))
    }

    @Test
    fun `the texts' licence links its deed, in swedish for the swedish app`() {
        assertEquals("CC BY-SA 4.0", WikipediaLinks.TEXT_LICENSE)
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/deed.sv", WikipediaLinks.textLicenseUrl(Locale.SV))
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", WikipediaLinks.textLicenseUrl(Locale.EN))
    }

    // Every text the app ships has the revision of its own language, so no shipped credit needs
    // the Wikidata fallback above.
    @Test
    fun `every shipped text has a revision in its own language`(
        @TempDir tempDir: Path,
    ) {
        val shipped = Path.of("../../composeApp/src/commonMain/composeResources/files/species.db")
        val db = Files.copy(shipped, tempDir.resolve("shipped.db"))
        val missing =
            java.sql.DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { conn ->
                conn
                    .createStatement()
                    .executeQuery(
                        """
                        SELECT t.species_id, t.locale, t.kind FROM SpeciesText t JOIN Species s ON s.id = t.species_id
                        WHERE t.kind IN ('description', 'migration', 'marginalia')
                          AND ((t.locale = 'sv' AND s.wikipedia_sv_revision IS NULL)
                            OR (t.locale = 'en' AND s.wikipedia_en_revision IS NULL))
                        """.trimIndent(),
                    ).use { rs ->
                        buildList { while (rs.next()) add("${rs.getString(1)} ${rs.getString(2)} ${rs.getString(3)}") }
                    }
            }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `a photo carries its commons file name and the links of its credit`(
        @TempDir tempDir: Path,
    ) = runTest {
        val driver = fixture(tempDir)
        val image =
            SqlDelightSpeciesRepository(BirdyContent(driver))
                .getById(talgoxe, Locale.SV)
                .first()!!
                .images
                .single()

        assertEquals("Parus_major_-_garden.jpg", image.commonsFileName)
        assertEquals("https://commons.wikimedia.org/wiki/File:Parus_major_-_garden.jpg", image.filePageUrl)
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", image.licenseUrl)
        assertEquals(false, image.isPublicDomain)
        driver.close()
    }
}
