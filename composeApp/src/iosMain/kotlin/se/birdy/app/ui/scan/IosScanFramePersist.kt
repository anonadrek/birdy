@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.scan

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import se.birdy.app.toByteArray
import se.birdy.app.toNSData
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput

// Matchar Androids YuvToJpeg-kvalitet (85) för fryst-frame-persist.
private const val FREEZE_JPEG_QUALITY = 0.85

/**
 * Encodar en [FrameFormat.BGRA_8888]-frame till JPEG via CGBitmapContext. BGRA är
 * CoreGraphics native-layout: `byteOrder32Little + premultipliedFirst` (alpha är 255
 * från kameran → premultiply är no-op). Returnerar null på fel format/storlek eller
 * CG-fel — aldrig en tyst felaktig bild.
 *
 * Källbytesen hålls pinnade genom HELA operationen inkl. [UIImageJPEGRepresentation]:
 * CGBitmapContextCreateImage kan backa sina pixlar copy-on-write mot vår buffert
 * (samma resonemang som ImagePreprocessor.ios.kt:s scaleRgba, i2b).
 */
internal fun encodeBgraFrameToJpeg(input: ImageInput): ByteArray? {
    if (input.format != FrameFormat.BGRA_8888) return null
    val expected = input.widthPx * input.heightPx * 4
    if (input.bytes.size != expected) return null
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    try {
        return input.bytes.usePinned { pinned ->
            val ctx =
                CGBitmapContextCreate(
                    pinned.addressOf(0),
                    input.widthPx.convert(),
                    input.heightPx.convert(),
                    8.convert(),
                    (input.widthPx * 4).convert(),
                    colorSpace,
                    BGRA_BITMAP_INFO,
                ) ?: return@usePinned null
            try {
                val image = CGBitmapContextCreateImage(ctx) ?: return@usePinned null
                try {
                    val ui = UIImage.imageWithCGImage(image)
                    UIImageJPEGRepresentation(ui, FREEZE_JPEG_QUALITY)?.toByteArray()
                } finally {
                    CGImageRelease(image)
                }
            } finally {
                CGContextRelease(ctx)
            }
        }
    } finally {
        CGColorSpaceRelease(colorSpace)
    }
}

/**
 * Freeze-persist för iOS-hosten: BGRA-frame → JPEG → `NSCachesDirectory/scan-frames/
 * <uuid>.jpg`. Kastar på encode-/skrivfel — ScanViewModel.onFreeze:s runCatching avbryter
 * då frysen, exakt som Android-hostens `file.outputStream().use { … }`.
 */
internal fun persistScanFrame(input: ImageInput): String {
    val jpeg =
        when (input.format) {
            // Defensivt: skulle en redan-JPEG-frame dyka upp (test/fake) persisteras den rakt av.
            FrameFormat.JPEG -> input.bytes
            else ->
                encodeBgraFrameToJpeg(input)
                    ?: error("scan persist: BGRA→JPEG encode failed for ${input.widthPx}x${input.heightPx}")
        }
    val dir = scanFramesDir()
    memScoped {
        val errorVar = alloc<ObjCObjectVar<NSError?>>()
        val created =
            NSFileManager.defaultManager.createDirectoryAtPath(
                path = dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = errorVar.ptr,
            )
        if (!created) {
            error("scan persist: failed to create directory $dir: ${errorVar.value?.localizedDescription}")
        }
    }
    val path = "$dir/${NSUUID().UUIDString}.jpg"
    if (!jpeg.toNSData().writeToFile(path, atomically = true)) {
        error("scan persist: failed to write $path")
    }
    return path
}

private fun scanFramesDir(): String {
    val caches =
        NSFileManager.defaultManager
            .URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
            .firstOrNull() as? NSURL
    val base = caches?.path ?: NSTemporaryDirectory()
    return "$base/scan-frames"
}

// 32-bit BGRA i minnet = little-endian ARGB: byteOrder32Little + premultipliedFirst.
private val BGRA_BITMAP_INFO: UInt =
    CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little
