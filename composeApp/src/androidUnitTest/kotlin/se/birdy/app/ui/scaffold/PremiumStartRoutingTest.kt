package se.birdy.app.ui.scaffold

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.data.premium.FormattedPrices
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Plan 3 Task 1 (spec 2026-09-24 §5.1, §5.2, §6.1): what the REAL AppScaffold
 * shows at start for each premium situation, and which screen the Premium route renders. The
 * whole NavHost runs under Robolectric with fakes, so the start effect's order (Play answered,
 * still on Listen, early member first, then the paywall rules) is exercised together with the
 * Premium route's thank-you/paywall branch.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PremiumStartRoutingTest {
    @get:Rule
    val compose = createComposeRule()

    private val thanksSv = "Du var med innan Birdy började ta betalt"

    // The purchase screen's headline. Its close button can't tell the two screens apart any more:
    // the thank-you has a close button too since release 1.3.0 Task 7b.
    private val paywallSv = "Hela året som"
    private val listenSv = "Kika"

    @Test
    @Config(qualifiers = "+sv")
    fun `early user sees the thank-you once at start`() {
        val prefs = FakeUserPreferences()
        compose.startAppScaffold(testAppGraph(prefs, installedAtMs = RoutingFixture.beforeCutoffMs))
        compose.onNodeWithText(thanksSv, substring = true).assertExists()
        compose.onNodeWithText(paywallSv, substring = true).assertDoesNotExist()
        assertTrue(runBlocking { prefs.grandfatherThanksShown.first() })
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `early user who already saw the thank-you gets neither thanks nor paywall`() {
        val prefs = FakeUserPreferences()
        runBlocking { prefs.setGrandfatherThanksShown(true) }
        compose.startAppScaffold(testAppGraph(prefs, installedAtMs = RoutingFixture.beforeCutoffMs))
        compose.onNodeWithText(listenSv).assertExists()
        compose.onNodeWithText(thanksSv, substring = true).assertDoesNotExist()
        compose.onNodeWithText(paywallSv, substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `debug skip override sends an early user to the paywall`() {
        val prefs = FakeUserPreferences()
        compose.startAppScaffold(
            testAppGraph(prefs, installedAtMs = RoutingFixture.beforeCutoffMs, debugSkipOverride = true),
        )
        compose.onNodeWithText(paywallSv, substring = true).assertExists()
        compose.onNodeWithText(thanksSv, substring = true).assertDoesNotExist()
        assertFalse(runBlocking { prefs.grandfatherThanksShown.first() })
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `new user after the cutoff gets the paywall and never the thank-you`() {
        val prefs = FakeUserPreferences()
        compose.startAppScaffold(testAppGraph(prefs, installedAtMs = RoutingFixture.afterCutoffMs))
        compose.onNodeWithText(paywallSv, substring = true).assertExists()
        compose.onNodeWithText(thanksSv, substring = true).assertDoesNotExist()
        assertTrue(runBlocking { prefs.postOnboardingPremiumShown.first() })
        assertFalse(runBlocking { prefs.grandfatherThanksShown.first() })
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `early user opening Premium sees the thank-you instead of the paywall`() {
        val prefs = FakeUserPreferences()
        runBlocking { prefs.setGrandfatherThanksShown(true) }
        val nav = compose.startAppScaffold(testAppGraph(prefs, installedAtMs = RoutingFixture.beforeCutoffMs))
        compose.runOnIdle { nav.navigate(AppRoute.Premium) }
        compose.waitForIdle()
        compose.onNodeWithText(thanksSv, substring = true).assertExists()
        compose.onNodeWithText(paywallSv, substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `paying subscriber opening Premium sees the paywall with buying switched off`() {
        val graph =
            testAppGraph(
                FakeUserPreferences(),
                installedAtMs = RoutingFixture.afterCutoffMs,
                backend = PremiumState.Active(PremiumTier.YEARLY, RoutingFixture.now),
                prices = FormattedPrices("199 kr", "499 kr"),
            )
        val nav = compose.startAppScaffold(graph)
        compose.onNodeWithText(paywallSv, substring = true).assertDoesNotExist()
        compose.runOnIdle { nav.navigate(AppRoute.Premium) }
        compose.waitForIdle()
        compose.onNodeWithText(paywallSv, substring = true).assertExists()
        compose.onNodeWithText(thanksSv, substring = true).assertDoesNotExist()
        // Prices are loaded, so the only reason the CTA is off is the active subscription.
        compose.onNodeWithText("Fortsätt").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `early user sees the English thank-you`() {
        compose.startAppScaffold(testAppGraph(FakeUserPreferences(), installedAtMs = RoutingFixture.beforeCutoffMs))
        compose.onNodeWithText("You joined before Birdy started charging", substring = true).assertExists()
        compose.onNodeWithText("A whole year as a", substring = true).assertDoesNotExist()
    }
}
