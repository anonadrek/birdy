package se.birdy.app.ui.settings

import android.content.Intent
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Work package 7i-fix E: Google Play policy 9900533 requires account settings to link to an
 * online, easy-to-use way to manage or cancel a subscription. Birdy only ever sells one real
 * Play subscription (the yearly plan) — Lifetime is a one-time purchase and the grandfather /
 * debug overrides are not real Play subscriptions either, so the row must show for YEARLY only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsManageSubscriptionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        premium: PremiumState,
        premiumOverride: PremiumState? = null,
    ) {
        attachComposeResourcesContext()
        SettingsLauncherSetup.init(RuntimeEnvironment.getApplication())
        compose.setContent {
            BirdyTheme {
                SettingsScreen(
                    viewModel =
                        SettingsViewModel(
                            prefs = FakeUserPreferences(),
                            premiumRepository = FakePremiumRepository(premium),
                            premiumOverride = premiumOverride,
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
    fun `row is visible for an active yearly subscription`() {
        show(PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hantera prenumeration"))
        compose.onNodeWithText("Hantera prenumeration").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `row is visible for an active yearly subscription, english`() {
        show(PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Manage subscription"))
        compose.onNodeWithText("Manage subscription").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `row is hidden for a lifetime purchase`() {
        show(PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()))
        compose.onNodeWithText("Hantera prenumeration").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `row is hidden for a free user`() {
        show(PremiumState.Free)
        compose.onNodeWithText("Hantera prenumeration").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `row is hidden for an early member override, which is lifetime, not a subscription`() {
        show(PremiumState.Free, premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()))
        compose.onNodeWithText("Hantera prenumeration").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `tapping the row opens the subscription center with the sku and the running package name`() {
        show(PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hantera prenumeration"))
        compose.onNodeWithText("Hantera prenumeration").performClick()
        compose.waitForIdle()

        val application = RuntimeEnvironment.getApplication()
        val started: Intent = Shadows.shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        val url = started.data.toString()
        assertTrue(url.startsWith("https://play.google.com/store/account/subscriptions"), url)
        assertTrue(url.contains("sku=premium_yearly_v1"), url)
        assertTrue(url.contains("package=${application.packageName}"), url)
    }
}
