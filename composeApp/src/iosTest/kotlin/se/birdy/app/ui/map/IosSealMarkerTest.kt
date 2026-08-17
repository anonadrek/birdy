@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.map

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.posix.memcpy
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression lock (review fix round 1, 2026-08-17): both `CGContextSetShadowWithColor` passes
 * in [buildBirdySealMarkerImage] rendered NOTHING before this fix — (a) the disc pass clipped to
 * the oval BEFORE drawing, so Quartz clipped the shadow halo away with the content; (b) the
 * shared `shadowColor` was extracted from a temporary `UIColor` with no live Kotlin owner, so the
 * `CGColorRef` could be a dangling/degenerate reference by the time either shadow call ran —
 * `CGContextSetShadowWithColor` treats that like `NULL` (documented: disables shadowing). This
 * test decodes the built marker's actual pixels and asserts non-zero alpha just OUTSIDE both
 * shadow-casting shapes (disc + point), where nothing but the shadow should ever paint.
 */
class IosSealMarkerTest {
    @Test
    fun bothShadowPassesPaintPixelsOutsideTheShapeEdges() {
        val marker = buildBirdySealMarkerImage(syntheticBirdImage())
        assertNotNull(marker, "buildBirdySealMarkerImage returned null")

        val buf = renderToPixelBuffer(marker)
        val (pointW, pointH) = marker.size.useContents { width to height }
        val scaleX = buf.width / pointW
        val scaleY = buf.height / pointH

        fun px(pt: Double) = (pt * scaleX).roundToInt()

        fun py(pt: Double) = (pt * scaleY).roundToInt()

        val s = MapMarkerSpec
        val cx = s.markerWidth() / 2.0
        val cy = (s.PADDING + s.DISC_DIAMETER / 2).toDouble()
        val r = (s.DISC_DIAMETER / 2).toDouble()

        // Negativ kontroll: ett hörn långt från allt ritat innehåll ska vara opåverkat — det
        // bevisar att pixel-läsningen själv är kalibrerad rätt, inte bara "alltid sant".
        assertEquals(0, buf.alphaAt(0, 0), "corner pixel should stay fully transparent")

        // Disc-passets skugga: 1pt utanför koppar-ringens/discens yttre kant (radie r), vid
        // discens vertikala mitt — varken disc, gradient eller ring målar hit, bara skuggan.
        val discShadowAlpha = buf.alphaAt(px(cx - r - 1.0), py(cy))
        assertTrue(discShadowAlpha > 0, "expected disc-shadow alpha > 0 just outside the disc, got $discShadowAlpha")

        // Spets-passets skugga: 1pt nedanför spetsens spets — utanför den fyllda triangeln.
        val pointTipY = cy + r + s.POINT_HEIGHT
        val pointShadowAlpha = buf.alphaAt(px(cx), py(pointTipY + 1.0))
        assertTrue(pointShadowAlpha > 0, "expected point-shadow alpha > 0 just below the tip, got $pointShadowAlpha")
    }
}

/** Any opaque, non-empty image works — [buildBirdySealMarkerImage] only tints/scales it. */
private fun syntheticBirdImage(): UIImage {
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(10.0, 10.0), false, 0.0)
    UIColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0).setFill()
    UIBezierPath.bezierPathWithRect(CGRectMake(0.0, 0.0, 10.0, 10.0)).fill()
    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return image ?: error("synthetic test bird image failed to render")
}

private class PixelBuffer(
    val bytes: UByteArray,
    val width: Int,
    val height: Int,
) {
    /** Alpha byte of the RGBA-premultiplied-last pixel at (x, y) — 0 means untouched canvas. */
    fun alphaAt(
        x: Int,
        y: Int,
    ): Int = bytes[(y * width + x) * 4 + 3].toInt()
}

/** Draws [image]'s CGImage 1:1 (actual pixel dims, no rescale) into an RGBA8888 buffer. */
private fun renderToPixelBuffer(image: UIImage): PixelBuffer {
    val cgImage = image.CGImage ?: error("UIImage has no CGImage")
    val w = CGImageGetWidth(cgImage).toInt()
    val h = CGImageGetHeight(cgImage).toInt()
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    // Same bitmap-info constant as shared/ml's ImagePreprocessor.ios.kt (byte-parity-tested
    // elsewhere): alpha is the LAST byte of each 4-byte pixel, which is all this test reads.
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big
    val ctx =
        CGBitmapContextCreate(null, w.convert(), h.convert(), 8.convert(), (w * 4).convert(), colorSpace, bitmapInfo)
            ?: error("CGBitmapContextCreate returned null")
    try {
        CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()), cgImage)
        val base = CGBitmapContextGetData(ctx)?.reinterpret<UByteVar>() ?: error("CGBitmapContextGetData returned null")
        val out = UByteArray(w * h * 4)
        out.usePinned { dst -> memcpy(dst.addressOf(0), base, (w * h * 4).convert()) }
        return PixelBuffer(out, w, h)
    } finally {
        CGContextRelease(ctx)
        CGColorSpaceRelease(colorSpace)
    }
}
