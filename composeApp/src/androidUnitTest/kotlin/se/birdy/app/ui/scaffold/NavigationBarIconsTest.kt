package se.birdy.app.ui.scaffold

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeUserPreferences
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7g item 6, on API 30 in the REAL AppScaffold: with three-button navigation
 * the buttons sit on whatever the screen draws at the bottom. The app's edge-to-edge setup asks
 * for dark buttons once, for the paper bottom bar, so on the dark moss Premium screens they were
 * dark on dark. AppScaffold now picks light buttons there and dark ones everywhere else.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "w411dp-h891dp-xxhdpi")
class NavigationBarIconsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** True when the window asks for dark navigation bar icons (for a light background). */
    private fun darkNavigationIcons(): Boolean {
        val window = compose.activity.window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `navigation buttons are dark over paper and light over the dark premium screens`() {
        val prefs = FakeUserPreferences()
        // An early member who already saw the thank-you: the start stays on Identifiera.
        runBlocking { prefs.setGrandfatherThanksShown(true) }
        val nav = compose.startAppScaffold(testAppGraph(prefs, RoutingFixture.beforeCutoffMs))
        assertTrue(darkNavigationIcons(), "Identifiera: paper bottom bar")

        // Early member: the Premium route shows the dark moss thank-you screen.
        compose.runOnIdle { nav.navigate(AppRoute.Premium) }
        compose.waitForIdle()
        assertFalse(darkNavigationIcons(), "Premium thank-you: dark moss to the bottom edge")

        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        assertTrue(darkNavigationIcons(), "back on Identifiera")
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the paywall itself gets light navigation buttons too`() {
        // A new user after the cutoff gets the day-0 paywall (dark moss) at start.
        val nav = compose.startAppScaffold(testAppGraph(FakeUserPreferences(), RoutingFixture.afterCutoffMs))
        assertTrue(nav.currentDestination?.hasRoute(AppRoute.Premium::class) == true, "the paywall is open")
        assertFalse(darkNavigationIcons(), "paywall: dark moss to the bottom edge")
    }
}
