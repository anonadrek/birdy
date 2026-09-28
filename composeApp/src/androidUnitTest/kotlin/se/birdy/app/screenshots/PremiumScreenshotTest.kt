package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    // The price node's line height is a proxy for its font size (T9c #1 guard): at the correct
    // ~22sp its DM Serif line box is ≈30.2dp tall; if the autosize-floor regression comes back
    // (price stuck at the 14sp minFontSize) it drops to ≈19.2dp — 25dp is the safe midpoint. A
    // screenshot alone wouldn't necessarily catch a subtle size regression like that.
    //
    // useUnmergedTree = true (T9d): TierCard's `.selectable()` merges all its descendants
    // (title/price/check-icon) into ONE semantics node ~75dp tall — the default MERGED tree
    // lookup finds that outer node instead of the price text itself, so the assertion would
    // pass at any price size, merged-node height included. The unmerged tree exposes the price
    // Text node directly.
    @Test
    @Config(qualifiers = "+sv")
    fun premium_sv() {
        compose.captureScreen("premium_sv") { screen(FormattedPrices("199 kr", "499 kr")) }
        compose.onNodeWithText("199 kr", useUnmergedTree = true).assertHeightIsAtLeast(25.dp)
    }

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

    // Stress case (T9b #9): a narrow phone (360dp, vs. the class default 411dp) + 130% system
    // font scale + long, realistic price strings ("SEK 1,499.00" is wider than any SEK amount
    // Birdy would plausibly charge, but Play formats can be this long for some locales/currencies)
    // — the two tier cards must stay equal height (IntrinsicSize.Min) and each price must stay on
    // one line (BasicText autoSize) instead of wrapping or overflowing its card.
    // Height taller than a real device (LazyColumn only composes what's in the viewport, so the
    // tier cards this test exists to check need to fall inside it, not below an unscrolled fold)
    // — width is the actual stress dimension here (360dp, narrower than the class default
    // 411dp), combined with 130% font scale and long price strings.
    @Test
    @Config(qualifiers = "en-w360dp-h1400dp")
    fun premium_w360_long_en_130() {
        RuntimeEnvironment.setFontScale(1.3f)
        compose.captureScreen("premium_w360_long_en_130") {
            screen(FormattedPrices(yearly = "SEK 1,499.00", lifetime = "SEK 199.00"))
        }
    }

    // Cheap to add alongside the EN stress case above: same narrow width + font scale, Swedish
    // price formatting (space thousands separator, comma decimal, trailing "kr").
    @Test
    @Config(qualifiers = "sv-w360dp-h1400dp")
    fun premium_w360_long_sv_130() {
        RuntimeEnvironment.setFontScale(1.3f)
        compose.captureScreen("premium_w360_long_sv_130") {
            screen(FormattedPrices(yearly = "1 499,00 kr", lifetime = "199,00 kr"))
        }
    }
}
