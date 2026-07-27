package se.birdy.ml

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRef
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.CoreGraphics.kCGInterpolationMedium
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.posix.memcpy

/**
 * iOS actual for [ImagePreprocessor]. Mirrors the Android reference
 * ([se.birdy.ml.ImagePreprocessor] androidMain) byte-for-byte: decode → apply
 * [ImageInput.rotationDegrees] → resize to `outWidth × outHeight` → emit a row-major,
 * RGB `FloatArray` normalized per channel as `out = (px/255 - mean) / std`.
 *
 * Decoding + resizing use CoreGraphics/ImageIO. Cardinal rotations (0/90/180/270) are an
 * exact, lossless pixel remap done in Kotlin so they are bit-exact regardless of the
 * CoreGraphics resampler. Resizing is the only inherently platform-approximate step
 * (Android bilinear vs. CoreGraphics interpolation); the byte-parity golden test avoids it
 * by using out-dims == in-dims.
 */
@OptIn(ExperimentalForeignApi::class)
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
        require(outWidth > 0 && outHeight > 0) { "Expected positive out dims, got ${outWidth}x$outHeight" }

        // A single sRGB space shared by every context: source images tagged sRGB (or
        // untagged) draw through without color-management shifts, matching Android's
        // sRGB Bitmap pipeline.
        val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB) ?: CGColorSpaceCreateDeviceRGB()
        try {
            val native =
                when (input.format) {
                    FrameFormat.RGBA_8888 -> decodeRgba(input)
                    FrameFormat.BGRA_8888 -> decodeBgra(input)
                    FrameFormat.JPEG -> decodeCompressed(input.bytes, colorSpace)
                    FrameFormat.YUV_420_888 ->
                        throw NotImplementedError(
                            "YUV_420_888 preprocessing is Android-only; iOS camera frames are BGRA_8888 (i2c).",
                        )
                }
            val oriented = rotate(native, input.rotationDegrees)
            val resized =
                if (oriented.width == outWidth && oriented.height == outHeight) {
                    oriented
                } else {
                    scaleRgba(oriented, outWidth, outHeight, colorSpace)
                }
            return normalize(resized, normalizationMean, normalizationStd)
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
    }

    /** RGBA8888 (4 bytes/px), row-major, top-left origin — the layout Android's getPixels reads. */
    private class Rgba(
        val bytes: UByteArray,
        val width: Int,
        val height: Int,
    )

    private fun decodeRgba(input: ImageInput): Rgba {
        val expected = input.widthPx * input.heightPx * 4
        require(input.bytes.size == expected) {
            "RGBA_8888 expects ${expected}B for ${input.widthPx}x${input.heightPx}, got ${input.bytes.size}"
        }
        return Rgba(input.bytes.asUByteArray().copyOf(), input.widthPx, input.heightPx)
    }

    private fun decodeBgra(input: ImageInput): Rgba {
        val expected = input.widthPx * input.heightPx * 4
        require(input.bytes.size == expected) {
            "BGRA_8888 expects ${expected}B for ${input.widthPx}x${input.heightPx}, got ${input.bytes.size}"
        }
        // Fused swizzle: the RGBA path already pays a defensive copy here (decodeRgba's
        // copyOf); reordering B↔R inside that same copy is the zero-extra-cost format
        // bridge — no separate per-frame conversion pass ever runs.
        val src = input.bytes.asUByteArray()
        val out = UByteArray(expected)
        var i = 0
        while (i < expected) {
            out[i] = src[i + 2]
            out[i + 1] = src[i + 1]
            out[i + 2] = src[i]
            out[i + 3] = src[i + 3]
            i += 4
        }
        return Rgba(out, input.widthPx, input.heightPx)
    }

    private fun decodeCompressed(
        bytes: ByteArray,
        colorSpace: CGColorSpaceRef?,
    ): Rgba {
        require(bytes.isNotEmpty()) { "Cannot decode empty image bytes" }
        val cfData =
            bytes.usePinned { pinned ->
                CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), bytes.size.convert())
            } ?: error("CFDataCreate returned null")
        try {
            val source = CGImageSourceCreateWithData(cfData, null) ?: error("Bytes are not a decodable image")
            try {
                val image = CGImageSourceCreateImageAtIndex(source, 0.convert(), null) ?: error("Image decode failed")
                try {
                    val w = CGImageGetWidth(image).toInt()
                    val h = CGImageGetHeight(image).toInt()
                    return renderToRgba(image, w, h, colorSpace)
                } finally {
                    CGImageRelease(image)
                }
            } finally {
                CFRelease(source)
            }
        } finally {
            CFRelease(cfData)
        }
    }

    /** Draws [image] into a fresh `w × h` premultiplied-RGBA context and copies out the pixels. */
    private fun renderToRgba(
        image: CGImageRef?,
        w: Int,
        h: Int,
        colorSpace: CGColorSpaceRef?,
    ): Rgba {
        val ctx =
            CGBitmapContextCreate(
                null,
                w.convert(),
                h.convert(),
                8.convert(),
                (w * 4).convert(),
                colorSpace,
                RGBA_BITMAP_INFO,
            ) ?: error("CGBitmapContextCreate (render) returned null")
        try {
            // Medium ≈ bilinear, tracking Android's createScaledBitmap(filter=true) more closely
            // than High (bicubic/Lanczos), which minimizes cross-platform resize drift.
            CGContextSetInterpolationQuality(ctx, kCGInterpolationMedium)
            CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()), image)
            val base =
                CGBitmapContextGetData(ctx)?.reinterpret<UByteVar>()
                    ?: error("CGBitmapContextGetData returned null")
            val out = UByteArray(w * h * 4)
            out.usePinned { dst -> memcpy(dst.addressOf(0), base, (w * h * 4).convert()) }
            return Rgba(out, w, h)
        } finally {
            CGContextRelease(ctx)
        }
    }

    /**
     * Resizes [src] to `outWidth × outHeight` via CoreGraphics. The source buffer is held pinned
     * across the *entire* operation — including [renderToRgba]'s `CGContextDrawImage` — so the
     * CGImage from `CGBitmapContextCreateImage` (which may back its pixels copy-on-write) never
     * references an unpinned Kotlin buffer, even if Kotlin/Native ever ships a compacting GC.
     */
    private fun scaleRgba(
        src: Rgba,
        outWidth: Int,
        outHeight: Int,
        colorSpace: CGColorSpaceRef?,
    ): Rgba =
        src.bytes.usePinned { pinned ->
            val srcCtx =
                CGBitmapContextCreate(
                    pinned.addressOf(0),
                    src.width.convert(),
                    src.height.convert(),
                    8.convert(),
                    (src.width * 4).convert(),
                    colorSpace,
                    RGBA_BITMAP_INFO,
                ) ?: error("CGBitmapContextCreate (source) returned null")
            try {
                val image = CGBitmapContextCreateImage(srcCtx) ?: error("CGBitmapContextCreateImage returned null")
                try {
                    // Draw happens inside this usePinned scope: src.bytes stays pinned through it.
                    renderToRgba(image, outWidth, outHeight, colorSpace)
                } finally {
                    CGImageRelease(image)
                }
            } finally {
                CGContextRelease(srcCtx)
            }
        }

    /**
     * Cardinal rotation matching Android's `Matrix.postRotate(degrees)` (positive = clockwise),
     * as an exact pixel remap. Only 0/90/180/270 occur in practice (device orientation).
     */
    private fun rotate(
        src: Rgba,
        degrees: Int,
    ): Rgba {
        when (((degrees % 360) + 360) % 360) {
            0 -> return src
            90 -> {
                // 90° CW: dst(row=c, col=H-1-r) = src(r,c); dst is H×W.
                val w = src.height
                val h = src.width
                val out = UByteArray(w * h * 4)
                remap(src, out, w) { r, c -> c to (src.height - 1 - r) }
                return Rgba(out, w, h)
            }
            180 -> {
                val w = src.width
                val h = src.height
                val out = UByteArray(w * h * 4)
                remap(src, out, w) { r, c -> (h - 1 - r) to (w - 1 - c) }
                return Rgba(out, w, h)
            }
            270 -> {
                // 270° CW (= 90° CCW): dst(row=W-1-c, col=r) = src(r,c); dst is H×W.
                val w = src.height
                val h = src.width
                val out = UByteArray(w * h * 4)
                remap(src, out, w) { r, c -> (src.width - 1 - c) to r }
                return Rgba(out, w, h)
            }
            else -> throw IllegalArgumentException("Only cardinal rotations (0/90/180/270) are supported, got $degrees")
        }
    }

    /** Copies every source pixel (4 bytes) to the destination offset produced by [dstRowCol]. */
    private inline fun remap(
        src: Rgba,
        dst: UByteArray,
        dstWidth: Int,
        dstRowCol: (row: Int, col: Int) -> Pair<Int, Int>,
    ) {
        for (r in 0 until src.height) {
            for (c in 0 until src.width) {
                val srcOff = (r * src.width + c) * 4
                val (dr, dc) = dstRowCol(r, c)
                val dstOff = (dr * dstWidth + dc) * 4
                dst[dstOff] = src.bytes[srcOff]
                dst[dstOff + 1] = src.bytes[srcOff + 1]
                dst[dstOff + 2] = src.bytes[srcOff + 2]
                dst[dstOff + 3] = src.bytes[srcOff + 3]
            }
        }
    }

    private fun normalize(
        rgba: Rgba,
        mean: FloatArray,
        std: FloatArray,
    ): FloatArray {
        val out = FloatArray(rgba.height * rgba.width * 3)
        var o = 0
        var p = 0
        repeat(rgba.height * rgba.width) {
            val r = rgba.bytes[p].toInt() / 255f
            val g = rgba.bytes[p + 1].toInt() / 255f
            val b = rgba.bytes[p + 2].toInt() / 255f
            p += 4
            out[o++] = (r - mean[0]) / std[0]
            out[o++] = (g - mean[1]) / std[1]
            out[o++] = (b - mean[2]) / std[2]
        }
        return out
    }

    private companion object {
        // 32-bit RGBA in memory (R,G,B,A byte order), alpha premultiplied — the only 8-bit RGBA
        // layout CoreGraphics accepts. Inputs are opaque (alpha 255) so premultiply is a no-op.
        val RGBA_BITMAP_INFO: UInt =
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big
    }
}
