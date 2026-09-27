package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.data.premium.FormattedPrices
import se.birdy.app.data.premium.PurchaseResult
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.app.ui.premium.PremiumScreen
import se.birdy.app.ui.premium.PremiumThankYouScreen
import se.birdy.app.ui.premium.PremiumViewModel

/**
 * Premium (Task 9, spec 2026-09-24 §4.1/§5.2): dark-moss-with-brass purchase screen + the
 * grandfathered thank-you screen. Replaces the old ThankYouScreenshotTest (its two methods now
 * live here alongside the purchase screen they share `premiumFeatures`/colors with).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PremiumScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun screen(prices: FormattedPrices) {
        val viewModel =
            remember {
                PremiumViewModel(
                    FakePremiumRepository(),
                    // A real Play purchase flow must never launch from a screenshot test — this
                    // stub reports a user cancel, which never completes/activates anything.
                    launchPurchase = { PurchaseResult.UserCancelled },
                    formattedPricesFlow = MutableStateFlow(prices),
                )
            }
        PremiumScreen(viewModel = viewModel, onClose = {}, onPurchaseComplete = {})
    }

    @Test
    @Config(qualifiers = "+sv")
    fun premium_sv() = compose.captureScreen("premium_sv") { screen(FormattedPrices("199 kr", "499 kr")) }

    @Test
    @Config(qualifiers = "+en")
    fun premium_en() = compose.captureScreen("premium_en") { screen(FormattedPrices("199 kr", "499 kr")) }

    // Prices not yet loaded from Play: the CTA is disabled and both tier cards show the
    // "Hämtar pris…" placeholder instead of a real price.
    @Test
    @Config(qualifiers = "+sv")
    fun premium_loading_sv() = compose.captureScreen("premium_loading_sv") { screen(FormattedPrices()) }

    @Test
    @Config(qualifiers = "+sv")
    fun thanks_sv() = compose.captureScreen("thanks_sv") { PremiumThankYouScreen(onClose = {}) }

    @Test
    @Config(qualifiers = "+en")
    fun thanks_en() = compose.captureScreen("thanks_en") { PremiumThankYouScreen(onClose = {}) }
}
