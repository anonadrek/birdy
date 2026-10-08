package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
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
import se.birdy.app.testing.installAssetPackImageLoader
import se.birdy.app.testing.resetImageLoader
import se.birdy.app.ui.stats.SeasonStatsFixtures
import se.birdy.app.ui.stats.SeasonStatsScreen
import se.birdy.content.Locale

/**
 * Säsongsstatistik, release 1.3.0 Task 7c (design option B, "the year ring and a journal of
 * firsts", approved by Albin 2026-10-06): the intro sentence, the year ring with the season sums,
 * the timeline of this year's first finds and the three most seen species as photo seals.
 *
 * The data is [SeasonStatsFixtures]: the approved mockup's own year (15 finds, five species, May
 * the best month, October in progress) on a FIXED clock, so "the current month" never drifts with
 * real time. The species' real plate photos come from the asset pack via
 * [installAssetPackImageLoader] (test-only), so the PNGs can be compared with the mockup.
 *
 * `_tall` variants use a 2600dp viewport because the screen is a virtualized `LazyColumn`: at the
 * normal w411dp-h891dp height the timeline and the seals are never composed at all. The large-text
 * guards with assertions live in the gated `SeasonStatsScreenTest`; this suite only renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class StatsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installAssetPackImageLoader()

    @After
    fun tearDown() = resetImageLoader()

    @Composable
    private fun screen(
        locale: Locale,
        observationRepo: FakeObservationRepository = SeasonStatsFixtures.mockupYearRepo(),
        speciesRepo: FakeSpeciesRepository = SeasonStatsFixtures.speciesWithPhotos(),
    ) {
        val vm = remember { SeasonStatsFixtures.viewModel(locale, observationRepo, speciesRepo) }
        SeasonStatsScreen(viewModel = vm, onBack = {})
    }

    @Test
    @Config(qualifiers = "+sv")
    fun stats_sv() {
        compose.captureScreen("stats_sv") { screen(Locale.SV) }
        compose.onNodeWithText("15 fynd. Maj var din bästa månad.").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun stats_en() {
        compose.captureScreen("stats_en") { screen(Locale.EN) }
        compose.onNodeWithText("15 finds. May was your best month.").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv-h2600dp")
    fun stats_sv_tall() {
        compose.captureScreen("stats_sv_tall") { screen(Locale.SV) }
        compose.onNodeWithText("MEST SEDDA").assertExists()
    }

    @Test
    @Config(qualifiers = "+en-h2600dp")
    fun stats_en_tall() {
        compose.captureScreen("stats_en_tall") { screen(Locale.EN) }
        compose.onNodeWithText("MOST SEEN").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun stats_empty_sv() {
        compose.captureScreen("stats_empty_sv") { screen(Locale.SV, observationRepo = FakeObservationRepository()) }
        compose.onNodeWithText("kartlägga").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv-h1600dp")
    fun stats_one_find_sv() {
        compose.captureScreen("stats_one_find_sv") { screen(Locale.SV, observationRepo = SeasonStatsFixtures.singleFindRepo()) }
        compose.onNodeWithText("Ditt första fynd i år kom i mars.").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv-h1600dp")
    fun stats_two_finds_sv() {
        compose.captureScreen("stats_two_finds_sv") { screen(Locale.SV, observationRepo = SeasonStatsFixtures.twoFindsRepo()) }
        compose.onNodeWithText("2 fynd hittills i år.").assertExists()
    }

    /** 1.3x and 2.0x on a narrow phone, with a long one-word species name in the timeline and the seals. */
    @Test
    @Config(qualifiers = "+sv-w360dp-h2600dp")
    fun stats_w360_sv_130() {
        RuntimeEnvironment.setFontScale(1.3f)
        compose.captureScreen("stats_w360_sv_130") { screen(Locale.SV, speciesRepo = SeasonStatsFixtures.speciesWithLongName()) }
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+sv-w360dp-h2600dp")
    fun stats_w360_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        compose.captureScreen("stats_w360_sv_200") { screen(Locale.SV, speciesRepo = SeasonStatsFixtures.speciesWithLongName()) }
        compose.assertNoTextLayoutRegressions()
    }
}
