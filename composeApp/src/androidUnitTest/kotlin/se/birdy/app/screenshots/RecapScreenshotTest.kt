package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.assertNoTextLayoutRegressions
import se.birdy.app.ui.recap.RecapFixtures
import se.birdy.app.ui.recap.RecapScreen

/**
 * Veckans uppslag, release 1.3.0 Task 7j (design "Uppslag 1", Albin's choice 2026-10-07): the
 * week's numbers in a header row, the Monday-to-Sunday strip, new species with their life-list
 * number, the week's stamp and every find as a grid.
 *
 * The data is [RecapFixtures]: the approved mockup's own week 41 (seven finds, three new species,
 * four weeks in a row, "Månads-rytm") on a fixed clock. The finds' own photos are stood in for by
 * each species' plate photo ([WithRealPhotos] with `findPlates`), untinted as in the app.
 * `_tall` variants use a tall viewport so the whole page is in one PNG. The assertions about the
 * screen live in the gated RecapScreenTest; this suite only renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class RecapScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** Each find's photo is its species' plate photo. */
    private val plates: Map<String, String> =
        RecapFixtures.weekFinds.associate { (id, qid, _) -> "$id.jpg" to "$qid/hero.webp" } +
            ("first.jpg" to "${RecapFixtures.TALGOXE}/hero.webp")

    @Composable
    private fun screen(
        english: Boolean = false,
        observations: FakeObservationRepository = RecapFixtures.mockupWeekRepo(),
        badges: FakeBadgeRepository = RecapFixtures.weekStampRepo(),
    ) {
        val vm =
            remember {
                RecapFixtures.viewModel(observations = observations, badges = badges, species = RecapFixtures.species(english))
            }
        WithRealPhotos(findPlates = plates) {
            RecapScreen(viewModel = vm, onOpenCamera = {}, onObservationClick = {}, onBack = {})
        }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun recap_sv() {
        compose.captureScreen("recap_sv") { screen() }
        compose.onNodeWithText("5 till 11 oktober. 5 dagar ute.").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv-h1200dp")
    fun recap_sv_tall() {
        compose.captureScreen("recap_sv_tall") { screen() }
        compose.onNodeWithText("ALLA FYND · 7").assertExists()
    }

    @Test
    @Config(qualifiers = "+en-h1200dp")
    fun recap_en_tall() {
        compose.captureScreen("recap_en_tall") { screen(english = true) }
        compose.onNodeWithText("ALL SIGHTINGS · 7").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv-h1200dp")
    fun recap_no_stamp_sv_tall() {
        compose.captureScreen("recap_no_stamp_sv_tall") { screen(badges = FakeBadgeRepository()) }
        compose.onNodeWithText("Ingen ny stämpel den här veckan.").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun recap_first_find_sv() {
        compose.captureScreen("recap_first_find_sv") {
            screen(observations = RecapFixtures.firstFindRepo(), badges = FakeBadgeRepository())
        }
        compose.onNodeWithText("5 till 11 oktober. 1 dag ute.").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun recap_empty_sv() {
        compose.captureScreen("recap_empty_sv") { screen(observations = RecapFixtures.quietWeekRepo(), badges = FakeBadgeRepository()) }
        compose.onNodeWithText("DIN STREAK").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun recap_empty_en() {
        compose.captureScreen("recap_empty_en") {
            screen(english = true, observations = RecapFixtures.quietWeekRepo(), badges = FakeBadgeRepository())
        }
        compose.onNodeWithText("YOUR STREAK").assertExists()
    }

    /** 2.0x on a narrow phone: the header stacks, the grid drops columns, no word is broken. */
    @Test
    @Config(qualifiers = "+sv-w320dp-h2100dp")
    fun recap_w320_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        compose.captureScreen("recap_w320_sv_200") { screen() }
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+en-w320dp-h2100dp")
    fun recap_w320_en_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        compose.captureScreen("recap_w320_en_200") { screen(english = true) }
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+sv-w320dp")
    fun recap_empty_w320_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        compose.captureScreen("recap_empty_w320_sv_200") {
            screen(observations = RecapFixtures.quietWeekRepo(), badges = FakeBadgeRepository())
        }
        compose.assertNoTextLayoutRegressions()
    }
}
