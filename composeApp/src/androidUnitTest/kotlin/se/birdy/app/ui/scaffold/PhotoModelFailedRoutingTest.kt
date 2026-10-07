package se.birdy.app.ui.scaffold

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.di.AppGraph
import se.birdy.app.testing.FakeUserPreferences
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Plan 3 Task 2c (Albin's 2026-10-05 decision): a failed photo model
 * ([se.birdy.ml.ClassifierBootstrapState.Failed]) stops only Scan (live camera) and photo-ID
 * (gallery/take photo), never the rest of the app. [AppGate] no longer gates on Failed (it goes
 * straight to [AppScaffold], same as Ready); [se.birdy.app.ui.scan.ScanScreenHost] and
 * [se.birdy.app.ui.photoanalyze.PhotoAnalyzeHost] each show
 * [se.birdy.app.ui.components.PhotoModelUnavailableView] instead of constructing their
 * ViewModel (which would throw reading [AppGraph.classifier]), with a "Try again" that calls
 * the bootstrap's retry().
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PhotoModelFailedRoutingTest {
    @get:Rule
    val compose = createComposeRule()

    private val listenSv = "Kika"
    private val titleSv = "Fotoigenkänningen kunde inte starta"
    private val retrySv = "Försök igen"
    private val bootstrapLoadingSv = "Förbereder fältboken…"

    // tab_listen (BottomNavBar's "Identifiera" tab, distinct from listen_card_camera_title
    // "Kika"): only rendered inside AppScaffold's Scaffold(bottomBar = ...), never inside
    // AppGate's own full-screen Initializing branch. A reliable tell for whether AppScaffold
    // itself (and therefore the NavHost/back stack it owns) is still mounted, independent of
    // which loading text happens to be on screen.
    private val bottomNavTabSv = "Identifiera"

    /**
     * An AppGraph that lands cleanly on Listen when driven through AppGate: onboarding already
     * seen, and (mirrors PremiumStartRoutingTest's "already saw the thank-you" setup) the
     * grandfather thank-you already shown so neither it nor the day-0 paywall intercepts the
     * start destination. [controllable] drives the photo-model bootstrap itself.
     */
    private fun graphWithBootstrap(controllable: ControllableClassifierBootstrap): AppGraph {
        val prefs = FakeUserPreferences()
        runBlocking {
            prefs.setHasSeenOnboarding(true)
            prefs.setGrandfatherThanksShown(true)
        }
        return testAppGraph(
            prefs,
            installedAtMs = RoutingFixture.beforeCutoffMs,
            classifierBootstrap = controllable.bootstrap,
        )
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `AppGate shows the start screen instead of an error when the bootstrap is Failed`() {
        val controllable = ControllableClassifierBootstrap(succeedOnAttempt = Int.MAX_VALUE)
        compose.startAppGate(graphWithBootstrap(controllable))
        compose.onNodeWithText(listenSv).assertExists()
        compose.onNodeWithText(titleSv).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `Listen keeps working with a Failed photo model`() {
        val controllable = ControllableClassifierBootstrap(succeedOnAttempt = Int.MAX_VALUE)
        compose.startAppGate(graphWithBootstrap(controllable))
        // The screen renders its own content (unrelated to the photo model); no error anywhere.
        compose.onNodeWithText(listenSv).assertExists()
        compose.onNodeWithText(titleSv).assertDoesNotExist()
        compose.onNodeWithText(retrySv).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `Scan shows the error with a working retry when the bootstrap is Failed`() {
        val controllable = ControllableClassifierBootstrap(succeedOnAttempt = 2)
        val nav = compose.startAppGate(graphWithBootstrap(controllable))
        assertEquals(1, controllable.buildAttempts.value)

        compose.runOnIdle { nav.navigate(AppRoute.Scan) }
        compose.waitForIdle()
        compose.onNodeWithText(titleSv).assertExists()
        compose.onNodeWithText(retrySv).assertExists()

        compose.onNodeWithText(retrySv).performClick()
        compose.waitForIdle()
        assertEquals(2, controllable.buildAttempts.value)
        compose.onNodeWithText(titleSv).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `Photo-ID shows the error with a working retry when the bootstrap is Failed`() {
        val controllable = ControllableClassifierBootstrap(succeedOnAttempt = 2)
        val nav = compose.startAppGate(graphWithBootstrap(controllable))
        assertEquals(1, controllable.buildAttempts.value)

        compose.runOnIdle { nav.navigate(AppRoute.PhotoAnalyze) }
        compose.waitForIdle()
        compose.onNodeWithText(titleSv).assertExists()
        compose.onNodeWithText(retrySv).assertExists()

        compose.onNodeWithText(retrySv).performClick()
        compose.waitForIdle()
        assertEquals(2, controllable.buildAttempts.value)
        compose.onNodeWithText(titleSv).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the English error text shows on Scan when the bootstrap is Failed`() {
        val controllable = ControllableClassifierBootstrap(succeedOnAttempt = Int.MAX_VALUE)
        val nav = compose.startAppGate(graphWithBootstrap(controllable))
        compose.runOnIdle { nav.navigate(AppRoute.Scan) }
        compose.waitForIdle()
        compose.onNodeWithText("Photo ID couldn't start").assertExists()
        compose.onNodeWithText("Try again").assertExists()
    }

    /**
     * Review fix (2026-10-05): `ClassifierBootstrap.retry()` sets the state back to Initializing
     * before rebuilding. AppGate used to render the full-screen loader for ANY Initializing,
     * tearing down the whole AppScaffold/NavHost subtree underneath it (bottom nav bar gone,
     * AppScaffold's own state and start-up effect reset) every time a retry fired from Scan or
     * PhotoAnalyze. This proves the full-screen loader is reserved for the very first build only;
     * the three tests below prove a later retry leaves AppScaffold (and the current route)
     * standing.
     */
    @Test
    @Config(qualifiers = "+sv")
    fun `the full screen loader covers only the very first build not a later retry`() {
        val controllable = ControllableClassifierBootstrap(succeedOnAttempt = 1, holdInitialBuild = true)
        compose.startAppGate(graphWithBootstrap(controllable))
        // The very first build is held: the full screen loader covers everything, Listen hasn't
        // rendered yet.
        compose.onNodeWithText(bootstrapLoadingSv).assertExists()
        compose.onNodeWithText(listenSv).assertDoesNotExist()

        compose.runOnIdle { controllable.initialBuildHold?.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithText(bootstrapLoadingSv).assertDoesNotExist()
        compose.onNodeWithText(listenSv).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `Scan stays on Scan and shows an in place loader while a retry rebuilds the model`() {
        val controllable = ControllableClassifierBootstrap(succeedOnAttempt = 2)
        val nav = compose.startAppGate(graphWithBootstrap(controllable))
        compose.runOnIdle { nav.navigate(AppRoute.Scan) }
        compose.waitForIdle()
        compose.onNodeWithText(titleSv).assertExists()

        val hold = controllable.armHoldForNextBuild()
        compose.onNodeWithText(retrySv).performClick()
        compose.waitForIdle()

        // Still on Scan, and AppScaffold (hence the bottom nav bar it owns) is still mounted:
        // the retry must not tear down AppScaffold/NavHost and fall back to AppGate's own
        // full-screen loader, which would hide the bottom nav entirely.
        assertTrue(nav.currentDestination?.hasRoute<AppRoute.Scan>() == true)
        compose.onNodeWithText(bottomNavTabSv).assertExists()
        compose.onNodeWithText(listenSv).assertDoesNotExist()
        compose.onNodeWithText(titleSv).assertDoesNotExist()
        compose.onNodeWithText(bootstrapLoadingSv).assertExists()

        compose.runOnIdle { hold.complete(Unit) }
        compose.waitForIdle()
        assertEquals(2, controllable.buildAttempts.value)
        assertTrue(nav.currentDestination?.hasRoute<AppRoute.Scan>() == true)
        compose.onNodeWithText(bottomNavTabSv).assertExists()
        compose.onNodeWithText(titleSv).assertDoesNotExist()
        compose.onNodeWithText(bootstrapLoadingSv).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `Photo-ID stays on Photo-ID and shows an in place loader while a retry rebuilds the model`() {
        val controllable = ControllableClassifierBootstrap(succeedOnAttempt = 2)
        val nav = compose.startAppGate(graphWithBootstrap(controllable))
        compose.runOnIdle { nav.navigate(AppRoute.PhotoAnalyze) }
        compose.waitForIdle()
        compose.onNodeWithText(titleSv).assertExists()

        val hold = controllable.armHoldForNextBuild()
        compose.onNodeWithText(retrySv).performClick()
        compose.waitForIdle()

        assertTrue(nav.currentDestination?.hasRoute<AppRoute.PhotoAnalyze>() == true)
        compose.onNodeWithText(bottomNavTabSv).assertExists()
        compose.onNodeWithText(listenSv).assertDoesNotExist()
        compose.onNodeWithText(titleSv).assertDoesNotExist()
        compose.onNodeWithText(bootstrapLoadingSv).assertExists()

        compose.runOnIdle { hold.complete(Unit) }
        compose.waitForIdle()
        assertEquals(2, controllable.buildAttempts.value)
        assertTrue(nav.currentDestination?.hasRoute<AppRoute.PhotoAnalyze>() == true)
        compose.onNodeWithText(bottomNavTabSv).assertExists()
        compose.onNodeWithText(titleSv).assertDoesNotExist()
        compose.onNodeWithText(bootstrapLoadingSv).assertDoesNotExist()
    }
}
