package se.birdy.app.photo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import se.birdy.app.toNSData
import se.birdy.app.ui.photoanalyze.drawAndEncodeJpeg
import se.birdy.app.ui.photoanalyze.scaleToLongSide
import kotlin.math.roundToInt

actual object PhotoStorageProvider {
    private val storage = IosPhotoStorage()

    actual fun get(): PhotoStorage = storage
}

private const val LONGEST_SIDE_PX = 1024
private const val PERSIST_JPEG_QUALITY = 0.85

/**
 * iOS impl av [PhotoStorage]-kontraktet (i2c): decode → longest side ≤1024 @ q85 →
 * `Documents/observations/<uuid>.jpg`. Spegel av AndroidPhotoStorage; skalningen
 * återanvänder IosImageDecode:s drawAndEncodeJpeg (kCGInterpolationMedium — parity-valet).
 */
@OptIn(ExperimentalForeignApi::class)
class IosPhotoStorage : PhotoStorage {
    override suspend fun persistJpeg(bytes: ByteArray): String =
        withContext(Dispatchers.Default) {
            if (bytes.isEmpty()) throw FrameUnavailableException("Empty JPEG bytes")
            // UIImage(data:) is a failable ObjC initializer, but this cinterop binding throws a
            // raw NullPointerException instead of yielding Kotlin null for undecodable bytes
            // (verified: garbage bytes crash the constructor, not just `?:` below) — catch it
            // explicitly so a bad gallery/frame input surfaces as our contract's exception, not
            // an uncaught native crash.
            val image =
                try {
                    UIImage(data = bytes.toNSData())
                } catch (_: NullPointerException) {
                    null
                } ?: throw FrameUnavailableException("Undecodable JPEG bytes")
            val (w, h) = image.size.useContents { width to height }
            if (w <= 0.0 || h <= 0.0) throw FrameUnavailableException("Degenerate image ${w}x$h")
            val (targetW, targetH) = scaleToLongSide(w.roundToInt(), h.roundToInt(), LONGEST_SIDE_PX)
            val jpeg =
                drawAndEncodeJpeg(image, targetW, targetH, quality = PERSIST_JPEG_QUALITY)
                    ?: throw FrameUnavailableException("JPEG re-encode failed")
            val docs =
                NSFileManager.defaultManager
                    .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                    .first() as NSURL
            val dir = docs.path + "/observations"
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
            val path = "$dir/${NSUUID().UUIDString}.jpg"
            if (!jpeg.toNSData().writeToFile(path, true)) error("photo persist: failed to write $path")
            path
        }

    override suspend fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}
