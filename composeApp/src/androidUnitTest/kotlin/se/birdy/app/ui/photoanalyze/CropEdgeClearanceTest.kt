package se.birdy.app.ui.photoanalyze

import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.attachComposeResourcesContext
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Plan 3 Task 7 (device walkthrough, API 36 emulator, gesture navigation): a
 * landscape photo fills the crop area edge to edge, so its corner handles sat exactly on the left
 * and right screen edges. With gesture navigation those edges belong to the system back gesture:
 * dragging a corner inward was taken as "back" and closed the crop screen instead of cropping.
 * The crop area now keeps clear of the system gesture zones, and the photo is drawn with a small
 * margin inside it so the handles are never cut in half by the screen edge either.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class CropEdgeClearanceTest {
    @get:Rule
    val compose = createComposeRule()

    private val cropHintSv = "Dra i hörnen för att beskära bilden"

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    /** A wide (it fills the width) solid red photo. */
    private fun redWidePhoto(): ImageBitmap {
        val photo = ImageBitmap(800, 400)
        Canvas(photo).drawRect(Rect(0f, 0f, 800f, 400f), Paint().apply { color = Color.Red })
        return photo
    }

    /** Shows the crop screen with a wide photo and returns the host view. */
    private fun showWideCrop(): View {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CropAdjustScreen(image = redWidePhoto(), onRotate = {}, onConfirm = {}, onCancel = {})
        }
        compose.waitForIdle()
        return view
    }

    private fun assertCropAreaClearOfEdges(minClearance: Dp) {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val crop = compose.onNodeWithContentDescription(cropHintSv).getUnclippedBoundsInRoot()
        assertTrue(crop.left >= minClearance, "left edge clearance ${crop.left} < $minClearance")
        assertTrue(root.right - crop.right >= minClearance, "right edge clearance ${root.right - crop.right} < $minClearance")
    }

    @Test
    fun `the crop corners stay out of the back gesture zones at the screen edges`() {
        val view = showWideCrop()
        val gestureZonePx = 96 // 32dp at xxhdpi, a typical back-gesture zone
        compose.runOnIdle {
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of(gestureZonePx, 0, gestureZonePx, 0))
                    .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        compose.waitForIdle()

        assertCropAreaClearOfEdges(minClearance = 32.dp)
    }

    @Test
    fun `the photo is drawn clear of the screen edges so its corner handles show whole`() {
        val view = showWideCrop()
        val pixels =
            compose.runOnIdle {
                android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888).also {
                    view.draw(android.graphics.Canvas(it))
                }
            }
        val crop = compose.onNodeWithContentDescription(cropHintSv).getUnclippedBoundsInRoot()
        val midY = with(compose.density) { ((crop.top + crop.bottom) / 2).roundToPx() }
        val margin = with(compose.density) { 8.dp.roundToPx() }

        fun isRed(x: Int): Boolean {
            val pixel = pixels.getPixel(x, midY)
            return android.graphics.Color.red(pixel) > 200 && android.graphics.Color.green(pixel) < 60
        }

        assertTrue(!isRed(1), "the photo starts at the left screen edge")
        assertTrue(!isRed(pixels.width - 2), "the photo ends at the right screen edge")
        assertTrue(isRed(margin * 3), "the photo should be drawn just inside the left margin")
        assertTrue(isRed(pixels.width - margin * 3), "the photo should be drawn just inside the right margin")
    }
}
