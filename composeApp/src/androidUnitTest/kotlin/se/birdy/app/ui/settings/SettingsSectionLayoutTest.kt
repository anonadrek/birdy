package se.birdy.app.ui.settings

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.domain.premium.PremiumState
import kotlin.test.assertEquals

/**
 * Release 1.3.0 Task 7g item 4: the Location section looked different from the others. Its
 * header was "Plats" where the others are in capitals (KONTO, AVISERINGAR), and the note under
 * its card started 10 dp further left than the section headers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsSectionLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showSettings() {
        attachComposeResourcesContext()
        compose.setContent {
            BirdyTheme {
                SettingsScreen(
                    viewModel =
                        SettingsViewModel(
                            prefs = FakeUserPreferences(),
                            premiumRepository = FakePremiumRepository(PremiumState.Free),
                        ),
                    onBack = {},
                    onPremiumClick = {},
                    onNavigateToAbout = {},
                    onShowIntroAgain = {},
                    versionName = "1.3.0",
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the location header is in capitals like the other section headers`() {
        showSettings()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("PLATS"))
        compose.onNodeWithText("PLATS").assertExists()
        compose.onNodeWithText("Plats").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the english location header is in capitals too`() {
        showSettings()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("LOCATION"))
        compose.onNodeWithText("LOCATION").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the location note starts where the section headers start`() {
        showSettings()
        val note = "Lagras bara på din telefon. Behövs för kartan."
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(note))
        val noteLeft = compose.onNodeWithText(note).getUnclippedBoundsInRoot().left
        val headerLeft = compose.onNodeWithText("PLATS").getUnclippedBoundsInRoot().left
        assertEquals(headerLeft, noteLeft)
    }
}
