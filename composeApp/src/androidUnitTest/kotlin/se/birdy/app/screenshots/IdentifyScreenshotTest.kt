package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.dailybird.DailyBirdToday
import se.birdy.app.ui.listen.ListenLauncherScreen
import se.birdy.app.ui.listen.ListenLauncherViewModel

/**
 * Identify screen (spec 2026-09-24 §4.4.1): today's bird as a [se.birdy.app.ui.components.PhotoHero],
 * kicker + headline, one rust primary action and two hairline rows. Release 1.3.0 Task 7d (design
 * option B): the hero carries the date, the scientific name, "Läs om arten" / "Lyssna efter den"
 * and the challenge row; the ViewModel just exposes the tracker's state, so a fixed
 * [DailyBirdToday] is all it needs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class IdentifyScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun screen(
        caughtToday: Boolean = false,
        daysCaught: Int = 0,
        name: String = "Sävsångare",
        showPremiumBadgeTag: Boolean = false,
    ) {
        val viewModel =
            remember {
                ListenLauncherViewModel(
                    dailyBird =
                        MutableStateFlow(
                            DailyBirdToday(
                                date = LocalDate(2026, 10, 6),
                                speciesId = "Q25403",
                                name = name,
                                scientificName = "Acrocephalus schoenobaenus",
                                heroImagePath = null,
                                caughtToday = caughtToday,
                                daysCaught = daysCaught,
                            ),
                        ),
                    dailyBirdBadgeUnlocked = MutableStateFlow(!showPremiumBadgeTag),
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
        compose.onNodeWithContentDescription("Sävsångare", substring = true).assertHasClickAction()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun identify_caught_sv() = compose.captureScreen("identify_caught_sv") { screen(caughtToday = true, daysCaught = 1) }

    @Test
    @Config(qualifiers = "+en")
    fun identify_caught_en() = compose.captureScreen("identify_caught_en") { screen(caughtToday = true, daysCaught = 1) }

    // Large text: the buttons wrap and the challenge row grows; nothing may be clipped.
    @Test
    @Config(qualifiers = "+sv")
    fun identify_sv_fontscale_150() {
        RuntimeEnvironment.setFontScale(1.5f)
        compose.captureScreen("identify_sv_150") { screen() }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun identify_sv_fontscale_200_long_name() {
        RuntimeEnvironment.setFontScale(2.0f)
        compose.captureScreen("identify_sv_200") { screen(caughtToday = true, daysCaught = 2, name = "Halsbandsflugsnappare") }
    }

    // A user without Premium: the challenge row tags Dagens fågel-jägare as a Premium badge.
    @Test
    @Config(qualifiers = "+sv")
    fun identify_free_sv() = compose.captureScreen("identify_free_sv") { screen(showPremiumBadgeTag = true) }

    @Test
    @Config(qualifiers = "+sv")
    fun identify_free_sv_fontscale_200_long_name() {
        RuntimeEnvironment.setFontScale(2.0f)
        compose.captureScreen("identify_free_sv_200") {
            screen(caughtToday = true, daysCaught = 2, name = "Halsbandsflugsnappare", showPremiumBadgeTag = true)
        }
    }

    @Test
    @Config(qualifiers = "+en")
    fun identify_en() {
        compose.captureScreen("identify_en") { screen() }
        compose.onNodeWithContentDescription("Settings").assertHasClickAction()
        compose.onNodeWithContentDescription("Sävsångare", substring = true).assertHasClickAction()
    }
}
