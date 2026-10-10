package se.birdy.app.ui.components

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.StatusBarInset
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7b review: the back button's disc starts at the same height below the
 * status bar on every screen. The paper button (BackTopBar, on screens that start below the
 * status bar) and the dark glass one (PhotoBackButton, over a photo drawn behind the status bar)
 * sit side by side on a magenta page with a 24dp status bar; the first pixel row that isn't
 * magenta in each half is the top edge of that disc.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w411dp-h300dp-xxhdpi")
class BackButtonLevelTest {
    @get:Rule
    val compose = createComposeRule()

    private val statusBarPx = 72 // 24dp at xxhdpi

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    private fun Bitmap.isPage(
        x: Int,
        y: Int,
    ): Boolean {
        val pixel = getPixel(x, y)
        val distance =
            abs(android.graphics.Color.red(pixel) - 255) +
                android.graphics.Color.green(pixel) +
                abs(android.graphics.Color.blue(pixel) - 255)
        return distance < 90
    }

    private fun Bitmap.firstRowWithDisc(columns: IntRange): Int = (0 until height).first { y -> columns.any { x -> !isPage(x, y) } }

    @Test
    fun `the paper and the glass back button discs start at the same height`() {
        val inset = StatusBarInset(statusBarPx)
        lateinit var view: View
        compose.setContent {
            inset.Capture()
            view = LocalView.current
            BirdyTheme {
                Row(Modifier.fillMaxSize().background(Color.Magenta)) {
                    // A paper screen: AppScaffold's BelowStatusBar starts it below the status bar.
                    Box(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) { BackTopBar(onBack = {}) }
                    // A photo top drawn behind the status bar.
                    Box(Modifier.weight(1f).fillMaxHeight()) { PhotoBackButton(onBack = {}) }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { inset.apply() }
        compose.waitForIdle()

        val pixels =
            compose.runOnIdle {
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                    view.draw(android.graphics.Canvas(it))
                }
            }
        val half = pixels.width / 2
        val paperTop = pixels.firstRowWithDisc(0 until half)
        val glassTop = pixels.firstRowWithDisc(half until pixels.width)

        assertTrue(paperTop >= statusBarPx, "the paper disc starts under the status bar (row $paperTop)")
        assertTrue(glassTop >= statusBarPx, "the glass disc starts under the status bar (row $glassTop)")
        assertTrue(
            abs(paperTop - glassTop) <= 2,
            "paper disc starts at row $paperTop, glass disc at row $glassTop (status bar ends at $statusBarPx)",
        )
    }
}
