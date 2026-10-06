package se.birdy.app.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import java.io.ByteArrayOutputStream

/**
 * The frame's JPEG turned upright, for the photo Scan persists on freeze.
 *
 * CameraX hands ImageAnalysis frames in the sensor's orientation: on a phone held upright the
 * back camera gives a landscape buffer with [ImageInput.rotationDegrees] = 90. The classifier
 * applies that rotation itself (ImagePreprocessor), but the persisted frame is what the NoBird and
 * Disambig thumbnails show and what gets saved to the diary, so it has to be turned here, or every
 * live-scan photo lies on its side (found on the API 36 emulator in Release 1.3.0 Plan 3 Task 7;
 * the emulator's back camera has sensor orientation 90 like real phones).
 *
 * Returns [ImageInput.bytes] untouched when no rotation is needed. If the JPEG cannot be decoded
 * the original bytes are kept (a sideways photo beats a lost one) and the reason is logged.
 */
internal fun ImageInput.uprightJpegBytes(): ByteArray {
    val degrees = ((rotationDegrees % FULL_TURN) + FULL_TURN) % FULL_TURN
    if (degrees == 0 || format != FrameFormat.JPEG) return bytes
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return if (decoded == null) {
        android.util.Log.w("Birdy", "Scan frame could not be decoded; saved without turning it upright")
        bytes
    } else {
        rotatedJpeg(decoded, degrees)
    }
}

/** [decoded] turned [degrees] clockwise, as JPEG. Recycles [decoded]. */
private fun rotatedJpeg(
    decoded: Bitmap,
    degrees: Int,
): ByteArray {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    return try {
        ByteArrayOutputStream().use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, UPRIGHT_JPEG_QUALITY, out)
            out.toByteArray()
        }
    } finally {
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
    }
}

private const val FULL_TURN = 360

// The frame was already encoded once at 85 (YuvToJpeg); a slightly higher quality here keeps the
// second encode from adding visible loss.
private const val UPRIGHT_JPEG_QUALITY = 90
