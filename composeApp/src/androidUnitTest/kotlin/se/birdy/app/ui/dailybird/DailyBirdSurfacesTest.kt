package se.birdy.app.ui.dailybird

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.dailybird.DailyBirdToday
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.listen.ListenLauncherScreen
import se.birdy.app.ui.listen.ListenLauncherViewModel
import se.birdy.app.ui.scaffold.AppRoute
import se.birdy.app.ui.scaffold.BottomNavBar
import se.birdy.app.ui.theme.BirdyTheme
import kotlin.test.assertEquals

/**
 * Release 1.3.0 Task 7d (design option B): what the Identify hero, the strip and the tab dot say
 * and do, in both languages. The rendered pictures are in the opt-in screenshot suite
 * (IdentifyScreenshotTest, DailyBirdScreenshotTest); this runs in the normal gate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DailyBirdSurfacesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun bird(
        caughtToday: Boolean = false,
        daysCaught: Int = 0,
    ) = DailyBirdToday(
        date = LocalDate(2026, 10, 6),
        speciesId = "Q25403",
        name = "Sävsångare",
        scientificName = "Acrocephalus schoenobaenus",
        heroImagePath = null,
        caughtToday = caughtToday,
        daysCaught = daysCaught,
    )

    private val opened = mutableListOf<String>()
    private var audioOpened = 0

    private fun showHero(
        bird: DailyBirdToday,
        showPremiumBadgeTag: Boolean = false,
    ) {
        attachComposeResourcesContext()
        compose.setContent {
            BirdyTheme {
                ListenLauncherScreen(
                    viewModel =
                        remember {
                            ListenLauncherViewModel(
                                dailyBird = MutableStateFlow(bird),
                                dailyBirdBadgeUnlocked = MutableStateFlow(!showPremiumBadgeTag),
                            )
                        },
                    onCameraClick = {},
                    onPhotoClick = {},
                    onSettingsClick = {},
                    onNavigateToAudioScan = { audioOpened++ },
                    onSpeciesProfileClick = { opened += it },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun showStrip(bird: DailyBirdToday) {
        attachComposeResourcesContext()
        compose.setContent {
            BirdyTheme { DailyBirdStrip(bird = bird, onClick = { opened += bird.speciesId }) }
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the hero kicker carries todays date in swedish`() {
        showHero(bird())
        compose.onNodeWithText("DAGENS FÅGEL · TIS 6 OKT", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Acrocephalus schoenobaenus", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Här just nu", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the hero kicker carries todays date in english`() {
        showHero(bird())
        compose.onNodeWithText("BIRD OF THE DAY · TUE 6 OCT", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `talkback reads the date in full in english`() {
        showHero(bird())
        compose
            .onNodeWithContentDescription("Bird of the day, Tuesday 6 October: Sävsångare, Acrocephalus schoenobaenus.")
            .assertHasClickAction()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `read about it opens the profile and listen for it opens audio id`() {
        showHero(bird())
        compose.onNodeWithText("Läs om arten").assertHasClickAction().performClick()
        compose.onNodeWithText("Lyssna efter den").assertHasClickAction().performClick()
        compose.waitForIdle()
        assertEquals(listOf("Q25403"), opened)
        assertEquals(1, audioOpened)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the whole hero still opens the profile and reads date and names`() {
        showHero(bird())
        compose
            .onNodeWithContentDescription("Dagens fågel, tisdag 6 oktober: Sävsångare, Acrocephalus schoenobaenus.")
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf("Q25403"), opened)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the challenge row before a catch asks for a find today`() {
        showHero(bird(caughtToday = false, daysCaught = 0))
        compose
            .onNodeWithContentDescription(
                "Inte fångad idag. Spara ett fynd av arten idag. 0 av 3 dagar.",
                useUnmergedTree = true,
            ).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the challenge row after a catch on the first day says two days left`() {
        showHero(bird(caughtToday = true, daysCaught = 1))
        compose
            .onNodeWithContentDescription("Fångad idag. Två dagar kvar till märket. 1 av 3 dagar.", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the challenge row on the second day says one day left`() {
        showHero(bird(caughtToday = true, daysCaught = 2))
        compose
            .onNodeWithContentDescription("Fångad idag. En dag kvar till märket. 2 av 3 dagar.", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the challenge row past the target shows the real total`() {
        showHero(bird(caughtToday = true, daysCaught = 4))
        compose
            .onNodeWithContentDescription("Fångad idag. Märket är klart. 4 dagar.", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the challenge row in english`() {
        showHero(bird(caughtToday = true, daysCaught = 1))
        compose
            .onNodeWithContentDescription("Caught today. Two more days to the badge. 1 of 3 days.", useUnmergedTree = true)
            .assertExists()
    }

    // Albin 2026-10-07: Dagens fågel-jägare is a Premium badge; the row stays for everyone and
    // tags the badge for users without Premium (AppGraph passes its effective Premium state to the ViewModel).
    @Test
    @Config(qualifiers = "+sv")
    fun `without premium the challenge row reads out the premium badge tag`() {
        showHero(bird(), showPremiumBadgeTag = true)
        compose
            .onNodeWithContentDescription(
                "Inte fångad idag. Spara ett fynd av arten idag. 0 av 3 dagar. Premium-märke.",
                useUnmergedTree = true,
            ).assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `without premium the challenge row reads out the premium badge tag in english`() {
        showHero(bird(caughtToday = true, daysCaught = 1), showPremiumBadgeTag = true)
        compose
            .onNodeWithContentDescription(
                "Caught today. Two more days to the badge. 1 of 3 days. Premium badge.",
                useUnmergedTree = true,
            ).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `with premium the challenge row has no premium tag`() {
        showHero(bird(), showPremiumBadgeTag = false)
        compose
            .onNodeWithContentDescription("Inte fångad idag. Spara ett fynd av arten idag. 0 av 3 dagar.", useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithContentDescription("Premium-märke", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    // The row reads as one sentence (clearAndSetSemantics), so the tag's own text is checked here.
    @Test
    @Config(qualifiers = "+sv")
    fun `the premium badge tag says premium-märke`() {
        attachComposeResourcesContext()
        compose.setContent { BirdyTheme { DailyBirdPremiumBadgeTag() } }
        compose.onNodeWithText("PREMIUM-MÄRKE").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the strip names the bird and the status and opens the profile`() {
        showStrip(bird())
        compose.onNodeWithText("Inte fångad idag · 0 av 3 dagar", useUnmergedTree = true).assertExists()
        compose
            .onNodeWithContentDescription("Dagens fågel: Sävsångare. Inte fångad idag, 0 av 3 dagar.")
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf("Q25403"), opened)
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the strip after a catch in english`() {
        showStrip(bird(caughtToday = true, daysCaught = 1))
        compose.onNodeWithText("Caught today · 1 of 3 days", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the identify tab announces the dot only while it is shown`() {
        attachComposeResourcesContext()
        val dot = MutableStateFlow(true)
        compose.setContent {
            BirdyTheme {
                Column(Modifier.fillMaxWidth()) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = AppRoute.Listen) { composable<AppRoute.Listen> {} }
                    val showDot = dot.collectAsState().value
                    BottomNavBar(nav, dailyBirdDot = showDot)
                }
            }
        }
        val withDot = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Dagens fågel väntar")
        compose.onNode(withDot).assertExists()
        assertEquals(
            "Identifiera",
            compose
                .onNode(withDot)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
                .text,
        )
        dot.value = false
        compose.waitForIdle()
        compose.onNode(withDot).assertDoesNotExist()
    }
}
