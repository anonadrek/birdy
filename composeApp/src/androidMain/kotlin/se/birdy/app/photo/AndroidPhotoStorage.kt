package se.birdy.app.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

private const val LONGEST_SIDE_PX = 1024
private const val JPEG_QUALITY = 85

class AndroidPhotoStorage(
    private val filesDir: File,
) : PhotoStorage {
    override suspend fun persistJpeg(bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) throw FrameUnavailableException("Empty JPEG bytes")
            val bitmap =
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw FrameUnavailableException("BitmapFactory.decodeByteArray returned null")
            try {
                val scaled = scale(bitmap, LONGEST_SIDE_PX)
                val dir = File(filesDir, "observations").apply { mkdirs() }
                val target = File(dir, "${UUID.randomUUID()}.jpg")
                FileOutputStream(target).use { out ->
                    val ok = scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    if (!ok) throw IOException("Bitmap.compress returned false")
                }
                if (scaled !== bitmap) scaled.recycle()
                target.absolutePath
            } finally {
                bitmap.recycle()
            }
        }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) {
            runCatching { File(path).delete() }
        }
    }

    private fun scale(
        src: Bitmap,
        longestSide: Int,
    ): Bitmap {
        val maxSide = maxOf(src.width, src.height)
        if (maxSide <= longestSide) return src
        val ratio = longestSide.toFloat() / maxSide.toFloat()
        val w = (src.width * ratio).toInt()
        val h = (src.height * ratio).toInt()
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
