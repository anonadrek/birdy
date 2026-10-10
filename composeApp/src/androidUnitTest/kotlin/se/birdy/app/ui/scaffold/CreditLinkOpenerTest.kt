package se.birdy.app.ui.scaffold

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performFirstLinkClick
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.content.model.SpeciesTextSource
import kotlin.test.assertEquals

/**
 * Release 1.3.0 (7e-2 and 7i-fix A quality review): a credit link on the species profile opens
 * through the url opener AppScaffold provides for the whole app (`openExternalUrl` in the app, which
 * catches and logs a missing browser or a refusing handler), never Compose's default handler.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class CreditLinkOpenerTest {
    @get:Rule
    val compose = createComposeRule()

    private val koltrast =
        Species(
            id = SpeciesId("Q25234"),
            scientificName = "Turdus merula",
            taxonomy = SpeciesTaxonomy(family = "Turdidae", familySv = "Trastar", genus = "Turdus", iocOrder = "Passeriformes"),
            name = "Koltrast",
            abundance = Abundance.ALLMÄN,
            iucnStatus = "LC",
            regions = listOf("SE"),
            season = emptyMap(),
            description = "Koltrasten är en av våra vanligaste fåglar.",
            migration = "Stannfågel.",
            images = listOf(SpeciesImage("hero", "Q25234/hero.webp", 1, 1, "CC BY-SA 4.0", "Musicaline", "", "Turdus merula 1294.jpg")),
            textSources = listOf(SpeciesTextSource(Locale.SV, "57446811", "https://sv.wikipedia.org/w/index.php?oldid=57446811")),
        )

    @Test
    fun `a credit link on the species profile opens through the app's url opener`() {
        val opened = mutableListOf<String>()
        val repository = FakeSpeciesRepository().apply { byId.value = mapOf(koltrast.id to koltrast) }
        val prefs = FakeUserPreferences()
        runBlocking { prefs.setGrandfatherThanksShown(true) }
        val nav =
            compose.startAppScaffold(
                testAppGraph(prefs, installedAtMs = RoutingFixture.beforeCutoffMs, repository = repository),
                openUrl = { opened += it },
            )
        compose.runOnIdle { nav.navigate(AppRoute.SpeciesProfile(koltrast.id.raw)) }
        compose.waitForIdle()

        // The photo credit under the photo: its first link is the licence.
        compose.onAllNodesWithText("Foto: Musicaline", substring = true)[0].performFirstLinkClick()
        // The text credit: its first link is the article.
        compose.onAllNodesWithText("AI-sammanfattning", substring = true)[0].performFirstLinkClick()
        compose.runOnIdle {
            assertEquals(
                listOf("https://creativecommons.org/licenses/by-sa/4.0/", "https://sv.wikipedia.org/w/index.php?oldid=57446811"),
                opened,
            )
        }
    }
}
