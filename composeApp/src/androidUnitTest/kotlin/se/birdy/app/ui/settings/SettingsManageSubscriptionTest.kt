package se.birdy.app.ui.settings

import android.content.Intent
import android.net.Uri
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
import se.birdy.app.data.premium.PremiumProducts
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.assertEquals

/**
 * Work package 7i-fix E: Google Play policy 9900533 requires account settings to link to an
 * online, easy-to-use way to manage or cancel a subscription. Birdy only ever sells one real
 * Play subscription (the yearly plan) — Lifetime is a one-time purchase, and the row must track
 * the BILLING backend, never the premiumOverride that grants Premium without a Play purchase
 * (grandfathering, the debug "force yearly" toggle): see SettingsUiState.playSubscriptionTier.
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

    /** Scrolls to and asserts the Language row exists — proves the screen actually rendered the
     * Account card (rather than the row being "absent" only because the whole screen crashed or
     * never composed), so the negative row/caption assertions below can't pass for the wrong
     * reason. */
    private fun assertAccountCardRendered(languageLabel: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(languageLabel))
        compose.onNodeWithText(languageLabel).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `row and caption are visible for an active yearly subscription`() {
        show(PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hantera prenumeration"))
        compose.onNodeWithText("Hantera prenumeration").assertExists()
        compose.onNodeWithText("Säg upp eller ändra i Google Play.").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `row and caption are visible for an active yearly subscription, english`() {
        show(PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Manage subscription"))
        compose.onNodeWithText("Manage subscription").assertExists()
        compose.onNodeWithText("Cancel or change in Google Play.").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `row and caption are hidden for a lifetime purchase`() {
        show(PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()))
        assertAccountCardRendered("Språk")
        compose.onNodeWithText("Hantera prenumeration").assertDoesNotExist()
        compose.onNodeWithText("Säg upp eller ändra i Google Play.").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `row and caption are hidden for a free user`() {
        show(PremiumState.Free)
        assertAccountCardRendered("Språk")
        compose.onNodeWithText("Hantera prenumeration").assertDoesNotExist()
        compose.onNodeWithText("Säg upp eller ändra i Google Play.").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `row is hidden for a grandfathered lifetime override over a free billing backend`() {
        show(PremiumState.Free, premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()))
        assertAccountCardRendered("Språk")
        compose.onNodeWithText("Hantera prenumeration").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `row is shown for a grandfathered lifetime override when the billing backend also holds a real yearly subscription`() {
        // A grandfathered device whose Google account separately bought (or was granted) a real
        // yearly subscription must still see the cancel link — the override must never hide it.
        show(
            premium = PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()),
            premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()),
        )
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hantera prenumeration"))
        compose.onNodeWithText("Hantera prenumeration").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `row is hidden for a debug force-yearly override over a free billing backend`() {
        // The debug "force yearly" override grants Premium for testing without any real Play
        // purchase behind it — it must never fabricate a subscription to cancel.
        show(PremiumState.Free, premiumOverride = PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()))
        assertAccountCardRendered("Språk")
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
        val uri = Uri.parse(started.data.toString())
        assertEquals("https", uri.scheme)
        assertEquals("play.google.com", uri.host)
        assertEquals("/store/account/subscriptions", uri.path)
        assertEquals(PremiumProducts.YEARLY, uri.getQueryParameter("sku"))
        assertEquals(application.packageName, uri.getQueryParameter("package"))
    }
}
