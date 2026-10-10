package se.birdy.content

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import se.birdy.content.build.SpeciesDbBuilder
import se.birdy.content.build.SpeciesValidator
import se.birdy.content.build.SpeciesYamlParser
import se.birdy.content.db.BirdyContent
import se.birdy.content.model.PhotoCredit
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/**
 * Release 1.3.0 Task 7e-2: the photo credits behind the "Bildkällor" list under About, and the
 * licence and Commons links each credit carries (legal review §2).
 */
class PhotoCreditsTest {
    private fun fixtureRepository(tempDir: Path): Pair<SqlDelightSpeciesRepository, JdbcSqliteDriver> {
        val items = SpeciesYamlParser().parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val outDb = tempDir.resolve("species.db")
        SpeciesDbBuilder().build(
            items = items,
            sourceImageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            targetDb = outDb,
            targetImageRoot = tempDir.resolve("images"),
        )
        val driver = JdbcSqliteDriver("jdbc:sqlite:${outDb.toAbsolutePath()}")
        return SqlDelightSpeciesRepository(BirdyContent(driver)) to driver
    }

    /** A copy of the species.db the app ships, so a test can never change the committed file. */
    private fun shippedDb(tempDir: Path): Path {
        val shipped = Path.of("../../composeApp/src/commonMain/composeResources/files/species.db")
        assertTrue(Files.isRegularFile(shipped), "species.db missing at ${shipped.toAbsolutePath()}")
        return Files.copy(shipped, tempDir.resolve("shipped.db"))
    }

    @Test
    fun `a credit has the photographer the licence and both links, the species named per language`(
        @TempDir tempDir: Path,
    ) = runTest {
        val (repo, driver) = fixtureRepository(tempDir)

        val sv = repo.photoCredits(Locale.SV).single()
        assertEquals(SpeciesId("Q25485"), sv.speciesId)
        assertEquals("Talgoxe", sv.speciesName)
        assertEquals("Parus major", sv.scientificName)
        assertEquals("hero", sv.role)
        assertEquals("Pierre Dalous", sv.author)
        assertEquals("CC BY-SA 4.0", sv.license)
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", sv.licenseUrl)
        assertEquals("https://commons.wikimedia.org/wiki/File:Parus_major_-_garden.jpg", sv.filePageUrl)
        assertEquals("Great Tit", repo.photoCredits(Locale.EN).single().speciesName)
        assertEquals(1, repo.photoCount())
        driver.close()
    }

    @Test
    fun `the shipped database lists every photo once, with the known credits`(
        @TempDir tempDir: Path,
    ) = runTest {
        val db = shippedDb(tempDir)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${db.toAbsolutePath()}")
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

        val (rows, species) =
            DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { conn ->
                conn.createStatement().executeQuery("SELECT count(*), count(DISTINCT species_id) FROM SpeciesImage").use {
                    it.next()
                    it.getInt(1) to it.getInt(2)
                }
            }
        val sv = repo.photoCredits(Locale.SV)
        val en = repo.photoCredits(Locale.EN)
        assertTrue(rows > 2000, "only $rows photos in species.db")
        assertEquals(rows, sv.size)
        assertEquals(rows, en.size)
        assertEquals(rows, repo.photoCount())
        assertEquals(rows, sv.map { it.path }.toSet().size, "a photo listed twice")
        assertEquals(species, sv.map { it.speciesId }.toSet().size)

        val greatTitHero = sv.byPath("Q25485/hero.webp")
        assertEquals("Talgoxe", greatTitHero.speciesName)
        assertEquals("Hobbyfotowiki", greatTitHero.author)
        assertEquals("CC0", greatTitHero.license)
        assertEquals("https://creativecommons.org/publicdomain/zero/1.0/", greatTitHero.licenseUrl)
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg",
            greatTitHero.filePageUrl,
        )
        val blueTitHero = en.byPath("Q25404/hero.webp")
        assertEquals("Eurasian Blue Tit", blueTitHero.speciesName)
        assertEquals("Kathy Büscher", blueTitHero.author)
        assertEquals("CC BY 2.0", blueTitHero.license)
        assertEquals("https://creativecommons.org/licenses/by/2.0/", blueTitHero.licenseUrl)
        driver.close()
    }

    @Test
    fun `every licence a shipped photo carries is in the link table, public domain unlinked`(
        @TempDir tempDir: Path,
    ) {
        val licenses =
            DriverManager.getConnection("jdbc:sqlite:${shippedDb(tempDir).toAbsolutePath()}").use { conn ->
                conn.createStatement().executeQuery("SELECT DISTINCT license FROM SpeciesImage").use {
                    buildSet { while (it.next()) add(it.getString(1)) }
                }
            }
        assertEquals(emptySet<String>(), licenses - PhotoLicenses.DEED_URLS.keys, "licences without a table entry")
        licenses.filter { it != "Public domain" }.forEach { license ->
            assertTrue(PhotoLicenses.deedUrl(license)!!.startsWith("https://creativecommons.org/"), license)
        }
        assertNull(PhotoLicenses.deedUrl("Public domain"))
    }

    @Test
    fun `the link table has exactly the licences the build lets a photo have`() {
        assertEquals(SpeciesValidator.ALLOWED_LICENSES, PhotoLicenses.DEED_URLS.keys)
    }

    @Test
    fun `file page urls survive ampersands question marks quotes and non ascii names`() {
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Chroicocephalus_ridibundus_%26_Chroicocephalus_genei," +
                "_Parque_Nacional_de_Do%C3%B1ana.jpg",
            PhotoLicenses.commonsFilePageUrl("Chroicocephalus ridibundus & Chroicocephalus genei, Parque Nacional de Doñana.jpg"),
        )
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Was_guckst_Du%3F_-_Anthus_berthelotii_(46038841522).jpg",
            PhotoLicenses.commonsFilePageUrl("Was guckst Du? - Anthus berthelotii (46038841522).jpg"),
        )
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Tordo-comum_%22Turdus_philomelos%22_(52656473356).jpg",
            PhotoLicenses.commonsFilePageUrl("Tordo-comum \"Turdus philomelos\" (52656473356).jpg"),
        )
    }

    private fun List<PhotoCredit>.byPath(path: String): PhotoCredit = single { it.path == path }
}
