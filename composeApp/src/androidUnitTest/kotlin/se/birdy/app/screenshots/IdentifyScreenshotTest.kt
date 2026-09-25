package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.ui.listen.ListenLauncherScreen
import se.birdy.app.ui.listen.ListenLauncherViewModel
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.dailybird.SeasonTag

/**
 * Identify screen (spec 2026-09-24 §4.4.1): today's bird as a [se.birdy.app.ui.components.PhotoHero],
 * kicker + headline, one rust primary action and two hairline rows. The ViewModel is built with
 * synchronous fakes (mirrors [se.birdy.app.ui.listen.ListenLauncherViewModelTest]'s direct-construction
 * style) — `viewModelScope`'s `Dispatchers.Main.immediate` runs the whole non-suspending init body
 * inline on Robolectric's main thread, so `dailyBird` is already populated by the time `setContent`
 * runs; no extra idling beyond `captureScreen`'s `waitForIdle()` is needed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class IdentifyScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun screen() {
        val viewModel =
            remember {
                ListenLauncherViewModel(
                    selectDailyBird = { DailyBird(speciesId = "Q25485", seasonTag = SeasonTag.PRESENT) },
                    getSpeciesName = { "Rödhake" },
                    getSpeciesHeroPath = { null },
                    recordDailyBirdShown = { _, _ -> },
                    dailyBirdMatchCount = { 1 },
                    isDailyBirdCaught = { false },
                    huntTarget = 3,
                )
            }
        ListenLauncherScreen(
            viewModel = viewModel,
            onCameraClick = {},
            onPhotoClick = {},
            onSettingsClick = {},
            onNavigateToAudioScan = {},
            onSpeciesProfileClick = {},
        )
    }

    @Test
    @Config(qualifiers = "+sv")
    fun identify_sv() {
        compose.captureScreen("identify_sv") { screen() }
        compose.onNodeWithContentDescription("Inställningar").assertHasClickAction()
        compose.onNodeWithContentDescription("Rödhake", substring = true).assertHasClickAction()
    }

    @Test
    @Config(qualifiers = "+en")
    fun identify_en() {
        compose.captureScreen("identify_en") { screen() }
        compose.onNodeWithContentDescription("Settings").assertHasClickAction()
        compose.onNodeWithContentDescription("Rödhake", substring = true).assertHasClickAction()
    }
}
