package se.birdy.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.ui.premium.PremiumThankYouScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ThankYouScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+sv")
    fun thanks_sv() = compose.captureScreen("thanks_sv") { PremiumThankYouScreen(onClose = {}) }

    @Test
    @Config(qualifiers = "+en")
    fun thanks_en() = compose.captureScreen("thanks_en") { PremiumThankYouScreen(onClose = {}) }
}
