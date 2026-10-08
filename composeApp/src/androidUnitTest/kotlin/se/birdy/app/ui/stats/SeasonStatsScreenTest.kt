package se.birdy.app.ui.stats

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.assertNoTextLayoutRegressions
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.content.Locale

/**
 * Release 1.3.0 Task 7c: the redesigned Season Statistics screen (year ring, first finds, photo
 * seals) in the normal test gate. What TalkBack hears (the ring's month counts in words, each
 * first find as one sentence, section headings), the computed intro sentence, the empty and
 * few-finds states, and the large-text guard at 1.3x/1.5x/2.0x on a narrow phone. A tall viewport
 * (h2600dp) composes the whole LazyColumn without scrolling. The opt-in StatsScreenshotTest renders
 * the same states to PNG.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2600dp-xxhdpi")
class SeasonStatsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    private fun show(
        locale: Locale,
        observationRepo: FakeObservationRepository = SeasonStatsFixtures.mockupYearRepo(),
        speciesRepo: FakeSpeciesRepository = SeasonStatsFixtures.speciesWithPhotos(),
    ) {
        compose.setContent {
            BirdyTheme {
                val vm = remember { SeasonStatsFixtures.viewModel(locale, observationRepo, speciesRepo) }
                SeasonStatsScreen(viewModel = vm, onBack = {})
            }
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the intro sentence names the total and the best month`() {
        show(Locale.SV)
        compose.onNodeWithText("SÄSONG 2026").assertExists()
        compose.onNodeWithText("15 fynd. Maj var din bästa månad.").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the intro sentence in English`() {
        show(Locale.EN)
        compose.onNodeWithText("SEASON 2026").assertExists()
        compose.onNodeWithText("15 finds. May was your best month.").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the year ring tells TalkBack every month in words`() {
        show(Locale.SV)
        val ring = "Årsring för 2026 med fynd per månad, januari överst. 15 fynd totalt. 5 arter. januari: 1 fynd"
        compose.onNodeWithContentDescription(ring, substring = true).assertExists()
        compose.onNodeWithContentDescription("maj: 4 fynd", substring = true).assertExists()
        compose
            .onNodeWithContentDescription("oktober, pågår: 2 fynd. november: 0 fynd. december: 0 fynd", substring = true)
            .assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the year ring description in English`() {
        show(Locale.EN)
        compose
            .onNodeWithContentDescription("15 finds in total. 5 species. January: 1 find", substring = true)
            .assertExists()
        compose.onNodeWithContentDescription("May: 4 finds", substring = true).assertExists()
        compose.onNodeWithContentDescription("October, in progress: 2 finds", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the season sums are read as words`() {
        show(Locale.SV)
        compose.onNodeWithContentDescription("Vår: 7 fynd").assertExists()
        compose.onNodeWithContentDescription("Höst: 2 fynd").assertExists()
        compose.onNodeWithText("oktober, pågår").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `each first find is one sentence for TalkBack`() {
        show(Locale.SV)
        compose.onNodeWithText("NYA ARTER I ÅR").assert(isHeading())
        compose.onNodeWithContentDescription("Talgoxe, årets art nummer 1, först sedd 8 januari").assertExists()
        compose.onNodeWithContentDescription("Knölsvan, årets art nummer 5, först sedd 11 maj").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `first finds and seals in English`() {
        show(Locale.EN)
        compose.onNodeWithText("NEW SPECIES THIS YEAR").assert(isHeading())
        compose
            .onNodeWithContentDescription("Blåmes, species number 2 this year, first seen February 21")
            .assertExists()
        compose.onNodeWithText("MOST SEEN").assert(isHeading())
        compose.onNodeWithText("6 finds").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the three most seen species get a seal each`() {
        show(Locale.SV)
        compose.onNodeWithText("MEST SEDDA").assert(isHeading())
        compose.onNodeWithText("6 fynd").assertExists()
        compose.onNodeWithText("4 fynd").assertExists()
        compose.onNodeWithText("2 fynd").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `an empty year still draws the ring and says how to fill it`() {
        show(Locale.SV, observationRepo = FakeObservationRepository())
        compose.onNodeWithText("kartlägga").assertExists()
        compose
            .onNodeWithText("Inga fynd i år ännu. Spara ditt första så börjar årsringen fyllas.")
            .assertExists()
        // An empty year: the total and the current month, not twelve times "0 fynd".
        compose
            .onNodeWithContentDescription("Årsring för 2026 med fynd per månad, januari överst. 0 fynd totalt. oktober, pågår")
            .assertExists()
        compose.onNodeWithText("oktober, pågår").assertExists()
        compose.onNodeWithText("MEST SEDDA").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a single find names the month it came in`() {
        show(Locale.SV, observationRepo = SeasonStatsFixtures.singleFindRepo())
        compose.onNodeWithText("Ditt första fynd i år kom i mars.").assertExists()
        compose.onNodeWithContentDescription("Talgoxe, årets art nummer 1, först sedd 14 mars").assertExists()
        compose.onNodeWithText("1 fynd").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `two finds in two months name no best month`() {
        show(Locale.SV, observationRepo = SeasonStatsFixtures.twoFindsRepo())
        compose.onNodeWithText("2 fynd hittills i år.").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv-w360dp")
    fun `no text is clipped or broken mid-word at 1_3x on a narrow phone`() {
        RuntimeEnvironment.setFontScale(1.3f)
        show(Locale.SV, speciesRepo = SeasonStatsFixtures.speciesWithLongName())
        // Once in the timeline, once among the seals.
        compose.onAllNodesWithText("Svarthakedopping").assertCountEquals(2)
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+sv-w360dp")
    fun `no text is clipped or broken mid-word at 1_5x on a narrow phone`() {
        RuntimeEnvironment.setFontScale(1.5f)
        show(Locale.SV, speciesRepo = SeasonStatsFixtures.speciesWithLongName())
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+sv-w360dp")
    fun `no text is clipped or broken mid-word at 2x on a narrow phone`() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(Locale.SV, speciesRepo = SeasonStatsFixtures.speciesWithLongName())
        compose.onNodeWithText("Säsongsstatistik").assertExists()
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+en-w360dp")
    fun `no text is clipped or broken mid-word at 2x in English`() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(Locale.EN)
        compose.assertNoTextLayoutRegressions()
    }
}
