package se.birdy.app.ui.scaffold

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeUserPreferences
import kotlin.test.assertTrue

/**
 * Plan 3 Task 2: a double tap on a close/back control must never pop the screen underneath.
 * The two taps land while the clock is held still, i.e. before the NavHost has recomposed,
 * which is what a quick double tap during the exit fade does on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DoubleTapBackTest {
    @get:Rule
    val compose = createComposeRule()

    private fun doubleTap(node: () -> SemanticsNodeInteraction) {
        compose.mainClock.autoAdvance = false
        node().performSemanticsAction(SemanticsActions.OnClick)
        node().performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `double tap on the paywall close button keeps the start screen`() {
        val nav =
            compose.startAppScaffold(
                testAppGraph(FakeUserPreferences(), installedAtMs = RoutingFixture.afterCutoffMs),
            )
        compose.onNodeWithContentDescription("Stäng").assertExists()
        doubleTap { compose.onNodeWithContentDescription("Stäng") }
        compose.onNodeWithText("Kika").assertExists()
        compose.runOnIdle { assertTrue(nav.currentDestination?.hasRoute(AppRoute.Listen::class) == true) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `double tap on Continue on the thank-you keeps the start screen`() {
        compose.startAppScaffold(testAppGraph(FakeUserPreferences(), installedAtMs = RoutingFixture.beforeCutoffMs))
        compose.onNodeWithText("Du var med innan Birdy", substring = true).assertExists()
        doubleTap { compose.onNodeWithText("Fortsätt") }
        compose.onNodeWithText("Kika").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `double tap on back in About keeps the start screen`() {
        val prefs = FakeUserPreferences()
        runBlocking { prefs.setGrandfatherThanksShown(true) }
        val nav = compose.startAppScaffold(testAppGraph(prefs, installedAtMs = RoutingFixture.beforeCutoffMs))
        compose.runOnIdle { nav.navigate(AppRoute.About) }
        compose.waitForIdle()
        doubleTap { compose.onNodeWithContentDescription("Tillbaka") }
        compose.onNodeWithText("Kika").assertExists()
    }
}
