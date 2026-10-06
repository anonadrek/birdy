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

    /** A solid red photo of the given size. */
    private fun redPhoto(
        width: Int,
        height: Int,
    ): ImageBitmap {
        val photo = ImageBitmap(width, height)
        Canvas(photo).drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint().apply { color = Color.Red })
        return photo
    }

    /** Shows the crop screen with a wide photo (it fills the width) and returns the host view. */
    private fun showWideCrop(): View = showCrop(redPhoto(800, 400))

    private fun showCrop(photo: ImageBitmap): View {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CropAdjustScreen(image = photo, onRotate = {}, onConfirm = {}, onCancel = {})
        }
        compose.waitForIdle()
        return view
    }

    private fun drawn(view: View): android.graphics.Bitmap =
        compose.runOnIdle {
            android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888).also {
                view.draw(android.graphics.Canvas(it))
            }
        }

    private fun android.graphics.Bitmap.isRedAt(
        x: Int,
        y: Int,
    ): Boolean {
        val pixel = getPixel(x, y)
        return android.graphics.Color.red(pixel) > 200 && android.graphics.Color.green(pixel) < 60
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

    /**
     * Plan 3 Task 7 review: a tall photo fills the crop area's height, which put its top
     * handles half outside the area (and out of its touch area). The margin applies vertically too.
     */
    @Test
    fun `a tall photo is drawn clear of the top and bottom of the crop area`() {
        val view = showCrop(redPhoto(300, 900))
        val pixels = drawn(view)
        val crop = compose.onNodeWithContentDescription(cropHintSv).getUnclippedBoundsInRoot()
        val (top, bottom, midX) =
            with(compose.density) {
                Triple(crop.top.roundToPx(), crop.bottom.roundToPx(), ((crop.left + crop.right) / 2).roundToPx())
            }
        val margin = with(compose.density) { 8.dp.roundToPx() }

        assertTrue(!pixels.isRedAt(midX, top + 1), "the photo starts at the top of the crop area")
        assertTrue(!pixels.isRedAt(midX, bottom - 2), "the photo ends at the bottom of the crop area")
        assertTrue(pixels.isRedAt(midX, top + margin * 3), "the photo should be drawn just inside the top margin")
        assertTrue(pixels.isRedAt(midX, bottom - margin * 3), "the photo should be drawn just inside the bottom margin")
    }
}
