package se.birdy.ml.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

internal fun ImageProxy.toJpegBytes(quality: Int = 85): ByteArray {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]
    val ySize = yPlane.buffer.remaining()
    val uSize = uPlane.buffer.remaining()
    val vSize = vPlane.buffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yPlane.buffer.get(nv21, 0, ySize)
    vPlane.buffer.get(nv21, ySize, vSize)
    uPlane.buffer.get(nv21, ySize + vSize, uSize)
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), quality, out)
    return out.toByteArray()
}
