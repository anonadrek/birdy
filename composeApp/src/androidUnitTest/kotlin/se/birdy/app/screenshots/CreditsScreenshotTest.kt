package se.birdy.app.screenshots

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.settings.AboutScreen
import se.birdy.app.ui.settings.credits.LicenseFiles
import se.birdy.app.ui.settings.credits.LicenseTextScreen
import se.birdy.app.ui.settings.credits.Loadable
import se.birdy.app.ui.settings.credits.OpenSourceLicensesScreen
import se.birdy.app.ui.settings.credits.PhotoCreditsScreen
import se.birdy.app.ui.settings.credits.groupPhotoCredits
import se.birdy.app.ui.settings.credits.loadLicenseText
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.PhotoCredit
import java.io.File

/**
 * Release 1.3.0 (legal review 7i-fix B and C, Task 7e-2): About with its rewritten "Innehåll &
 * data", the photo credits (every photo of the shipped species.db) and the open-source licences,
 * SV and EN, at normal and 2.0× text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CreditsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun about(
        name: String,
        locale: Locale,
    ) = compose.captureScreen(name) {
        AboutScreen(
            onBack = {},
            version = "1.3.0",
            photoCount = 2066,
            locale = locale,
            onOpenPhotoCredits = {},
            onOpenLicenses = {},
        )
    }

    private fun photoCredits(
        name: String,
        locale: Locale,
    ) {
        val credits = groupPhotoCredits(shippedCredits(locale))
        compose.captureScreen(name) {
            PhotoCreditsScreen(state = Loadable.Loaded(credits), locale = locale, onBack = {})
        }
    }

    private fun licenses(name: String) {
        attachComposeResourcesContext()
        val index = runBlocking { LicenseFiles.index() }
        compose.captureScreen(name) {
            OpenSourceLicensesScreen(state = Loadable.Loaded(index), onBack = {}, onOpenEntry = {})
        }
    }

    private fun licenseText(
        name: String,
        entryId: String,
    ) {
        attachComposeResourcesContext()
        val text = runBlocking { loadLicenseText(entryId) }
        compose.captureScreen(name) { LicenseTextScreen(state = Loadable.Loaded(text), onBack = {}) }
    }

    @Test
    @Config(qualifiers = "+sv-h1800dp")
    fun about_sv() = about("about_sv", Locale.SV)

    @Test
    @Config(qualifiers = "+en-h1800dp")
    fun about_en() = about("about_en", Locale.EN)

    @Test
    @Config(qualifiers = "+sv-h2600dp")
    fun about_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        about("about_sv_200", Locale.SV)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun photo_credits_sv() = photoCredits("photo_credits_sv", Locale.SV)

    @Test
    @Config(qualifiers = "+en")
    fun photo_credits_en() = photoCredits("photo_credits_en", Locale.EN)

    @Test
    @Config(qualifiers = "+sv")
    fun photo_credits_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        photoCredits("photo_credits_sv_200", Locale.SV)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun licenses_sv() = licenses("licenses_sv")

    @Test
    @Config(qualifiers = "+en")
    fun licenses_en() = licenses("licenses_en")

    @Test
    @Config(qualifiers = "+en")
    fun licenses_en_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        licenses("licenses_en_200")
    }

    @Test
    @Config(qualifiers = "+sv")
    fun license_text_birdnet_sv() = licenseText("license_text_birdnet_sv", "birdnet-lite")

    @Test
    @Config(qualifiers = "+en")
    fun license_text_caveat_en() = licenseText("license_text_caveat_en", "font:caveat")

    /** The end of the list: the other images, after a short list of species photos. */
    @Test
    @Config(qualifiers = "+sv")
    fun photo_credits_other_sv() {
        val credits = groupPhotoCredits(shippedCredits(Locale.SV).filter { it.speciesId.raw in setOf("Q25485", "Q180991") })
        compose.captureScreen("photo_credits_other_sv") {
            PhotoCreditsScreen(state = Loadable.Loaded(credits), locale = Locale.SV, onBack = {})
        }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun license_text_native_sv() = licenseText("license_text_native_sv", "tensorflow-native")

    @Test
    @Config(qualifiers = "+sv")
    fun license_text_birdnet_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        licenseText("license_text_birdnet_sv_200", "birdnet-lite")
    }
}

/** Every photo credit in the species.db the app ships, read with the same join as `selectCredits`. */
private fun shippedCredits(locale: Locale): List<PhotoCredit> {
    val db =
        SQLiteDatabase.openDatabase(
            File("src/commonMain/composeResources/files/species.db").absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    return db.use {
        it
            .rawQuery(
                """
                SELECT i.species_id, i.role, i.path, i.license, i.author, i.commons_filename, s.scientific_name,
                       COALESCE(l.name, e.name, s.scientific_name)
                FROM SpeciesImage i JOIN Species s ON s.id = i.species_id
                LEFT JOIN SpeciesName l ON l.species_id = i.species_id AND l.locale = ?
                LEFT JOIN SpeciesName e ON e.species_id = i.species_id AND e.locale = 'en'
                """.trimIndent(),
                arrayOf(locale.code),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            PhotoCredit(
                                speciesId = SpeciesId(cursor.getString(0)),
                                speciesName = cursor.getString(7),
                                scientificName = cursor.getString(6),
                                role = cursor.getString(1),
                                path = cursor.getString(2),
                                license = cursor.getString(3),
                                author = cursor.getString(4),
                                commonsFileName = cursor.getString(5),
                            ),
                        )
                    }
                }
            }
    }
}
