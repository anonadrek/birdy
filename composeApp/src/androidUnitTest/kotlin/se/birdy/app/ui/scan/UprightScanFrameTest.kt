package se.birdy.app.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Plan 3 Task 7 (device walkthrough, API 36 emulator): CameraX hands ImageAnalysis
 * frames in the sensor's orientation (a landscape 640x480 buffer plus rotationDegrees = 90 on a
 * phone held upright). The classifier applies that rotation itself, but the frame Scan persists
 * on freeze was written as is, so the NoBird and Disambig thumbnails and the photo saved to the
 * diary lay on their side. The persisted frame must be turned upright first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UprightScanFrameTest {
    /** 40x20 landscape frame: left half red, right half blue. */
    private fun landscapeFrame(rotationDegrees: Int): ImageInput {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLUE)
        canvas.drawRect(0f, 0f, WIDTH / 2f, HEIGHT.toFloat(), Paint().apply { color = Color.RED })
        val jpeg =
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.toByteArray()
            }
        bitmap.recycle()
        return ImageInput(
            bytes = jpeg,
            widthPx = WIDTH,
            heightPx = HEIGHT,
            rotationDegrees = rotationDegrees,
            format = FrameFormat.JPEG,
        )
    }

    private fun isRed(pixel: Int) = Color.red(pixel) > 180 && Color.blue(pixel) < 80

    private fun isBlue(pixel: Int) = Color.blue(pixel) > 180 && Color.red(pixel) < 80

    @Test
    fun `a frame that needs 90 degrees is saved upright with its left side on top`() {
        val upright = landscapeFrame(rotationDegrees = 90).uprightJpegBytes()
        val decoded = BitmapFactory.decodeByteArray(upright, 0, upright.size)

        assertEquals(HEIGHT, decoded.width)
        assertEquals(WIDTH, decoded.height)
        assertTrue(isRed(decoded.getPixel(HEIGHT / 2, WIDTH / 8)), "the left half should end up on top")
        assertTrue(isBlue(decoded.getPixel(HEIGHT / 2, WIDTH * 7 / 8)), "the right half should end up at the bottom")
    }

    @Test
    fun `a frame that needs 270 degrees is saved upright with its left side at the bottom`() {
        val upright = landscapeFrame(rotationDegrees = 270).uprightJpegBytes()
        val decoded = BitmapFactory.decodeByteArray(upright, 0, upright.size)

        assertEquals(HEIGHT, decoded.width)
        assertEquals(WIDTH, decoded.height)
        assertTrue(isBlue(decoded.getPixel(HEIGHT / 2, WIDTH / 8)), "the right half should end up on top")
        assertTrue(isRed(decoded.getPixel(HEIGHT / 2, WIDTH * 7 / 8)), "the left half should end up at the bottom")
    }

    @Test
    fun `a frame that is already upright is saved byte for byte`() {
        val frame = landscapeFrame(rotationDegrees = 0)

        assertSame(frame.bytes, frame.uprightJpegBytes())
    }

    private companion object {
        const val WIDTH = 40
        const val HEIGHT = 20
    }
}
