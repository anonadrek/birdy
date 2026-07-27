package se.birdy.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

actual class ImagePreprocessor actual constructor() {
    actual fun preprocess(
        input: ImageInput,
        outHeight: Int,
        outWidth: Int,
        normalizationMean: FloatArray,
        normalizationStd: FloatArray,
    ): FloatArray {
        require(normalizationMean.size == 3) { "Expected mean[3], got ${normalizationMean.size}" }
        require(normalizationStd.size == 3) { "Expected std[3], got ${normalizationStd.size}" }

        val decoded = decode(input)
        val rotated = applyRotation(decoded, input.rotationDegrees)
        if (rotated !== decoded) decoded.recycle()
        val resized = Bitmap.createScaledBitmap(rotated, outWidth, outHeight, true)
        if (resized !== rotated) rotated.recycle()

        try {
            val out = FloatArray(outHeight * outWidth * 3)
            val pixels = IntArray(outHeight * outWidth)
            resized.getPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight)
            var idx = 0
            for (px in pixels) {
                val r = ((px shr 16) and 0xFF) / 255f
                val g = ((px shr 8) and 0xFF) / 255f
                val b = (px and 0xFF) / 255f
                out[idx++] = (r - normalizationMean[0]) / normalizationStd[0]
                out[idx++] = (g - normalizationMean[1]) / normalizationStd[1]
                out[idx++] = (b - normalizationMean[2]) / normalizationStd[2]
            }
            return out
        } finally {
            resized.recycle()
        }
    }

    private fun decode(input: ImageInput): Bitmap =
        when (input.format) {
            FrameFormat.JPEG ->
                BitmapFactory.decodeByteArray(input.bytes, 0, input.bytes.size)
                    ?: error("BitmapFactory.decodeByteArray returned null")
            FrameFormat.YUV_420_888 -> decodeYuv420(input)
            FrameFormat.RGBA_8888 -> decodeRgba(input)
            FrameFormat.BGRA_8888 ->
                error("BGRA_8888 frames are iOS-only; Android sources emit JPEG/YUV/RGBA")
        }

    private fun decodeRgba(input: ImageInput): Bitmap {
        val bmp = Bitmap.createBitmap(input.widthPx, input.heightPx, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(input.bytes))
        return bmp
    }

    /**
     * Decodes [ImageInput] bytes assumed to already be NV21-packed (Y plane followed by
     * interleaved VU). CameraX YUV_420_888 frames are NOT directly NV21 — upstream
     * callers must repack via [se.birdy.ml.camera.YuvToJpeg] (or equivalent) before
     * setting [FrameFormat.YUV_420_888] on the [ImageInput].
     */
    private fun decodeYuv420(input: ImageInput): Bitmap {
        val yuv = YuvImage(input.bytes, ImageFormat.NV21, input.widthPx, input.heightPx, null)
        val baos = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, input.widthPx, input.heightPx), YUV_TO_JPEG_QUALITY, baos)
        val jpeg = baos.toByteArray()
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: error("YUV→JPEG→Bitmap decode failed")
    }

    private fun applyRotation(
        bitmap: Bitmap,
        degrees: Int,
    ): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val YUV_TO_JPEG_QUALITY = 90
    }
}
