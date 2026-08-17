package se.birdy.app.ui.map

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawRadialGradient
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextSetShadowWithColor
import platform.CoreGraphics.CGGradientCreateWithColorComponents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGGradientDrawsAfterEndLocation
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate

private fun uiColor(argb: Long): UIColor =
    UIColor(
        red = ((argb shr 16) and 0xFF).toDouble() / 255.0,
        green = ((argb shr 8) and 0xFF).toDouble() / 255.0,
        blue = (argb and 0xFF).toDouble() / 255.0,
        alpha = ((argb shr 24) and 0xFF).toDouble() / 255.0,
    )

/**
 * iOS-tvillingen till buildBirdySealMarker (MapMarkerIcon.android.kt) — ritar från
 * samma MapMarkerSpec så plattformarna inte kan divergera. Mått i pt (≙ dp);
 * scale=0.0 ger enhetens naturliga skala. Returnerar null endast om ingen
 * grafikkontext kan skapas (då faller anroparen tillbaka på systempinnen).
 */
@OptIn(ExperimentalForeignApi::class)
internal fun buildBirdySealMarkerImage(bird: UIImage): UIImage? {
    val s = MapMarkerSpec
    val w = s.markerWidth()
    val h = s.markerHeight()
    val cx = w / 2.0
    val cy = (s.PADDING + s.DISC_DIAMETER / 2).toDouble()
    val r = (s.DISC_DIAMETER / 2).toDouble()

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(w.toDouble(), h.toDouble()), false, 0.0)
    val ctx =
        UIGraphicsGetCurrentContext() ?: run {
            UIGraphicsEndImageContext()
            return null
        }

    // Review-fix (2026-08-17): hålls som `val` genom BÅDA skugg-passen. `uiColor(s.SHADOW).CGColor`
    // inline (den gamla koden) extraherar CGColorRef ur en UIColor som saknar en Kotlin-ägare i
    // samma uttryck — K/N:s minneshantering kan då frigöra UIColor-omslaget innan
    // CGContextSetShadowWithColor hinner läsa det, vilket gör CGColorRef ogiltig/hängande.
    // CGContextSetShadowWithColor behandlar en ogiltig färg som NULL (dokumenterat: NULL
    // avaktiverar skuggan) — så BÅDA skugg-passen ritade tyst ingenting. Genom att hålla
    // `shadowUiColor` i en val som lever över hela funktionskroppen, och läsa `.CGColor` på nytt
    // vid varje anropsställe, garanteras en levande ägare vid varje CGContextSetShadowWithColor.
    val shadowUiColor = uiColor(s.SHADOW)

    // Nedåtspets (bakom discen), med skugga.
    CGContextSaveGState(ctx)
    CGContextSetShadowWithColor(ctx, CGSizeMake(0.0, s.SHADOW_DY.toDouble()), s.SHADOW_BLUR.toDouble(), shadowUiColor.CGColor)
    uiColor(s.COPPER).setFill()
    UIBezierPath()
        .apply {
            moveToPoint(CGPointMake(cx - s.POINT_HALF_WIDTH, cy + r - s.POINT_TOP_INSET))
            addLineToPoint(CGPointMake(cx + s.POINT_HALF_WIDTH, cy + r - s.POINT_TOP_INSET))
            addLineToPoint(CGPointMake(cx, cy + r + s.POINT_HEIGHT))
            closePath()
        }.fill()
    CGContextRestoreGState(ctx)

    // Cream-disc: en opak fyllning FÖRST, med skugga, INTE klippt. Review-fix (2026-08-17):
    // föregående kod klippte till discens oval FÖRE den ritade något — Quartz klipper skuggans
    // halo tillsammans med innehållet, så en skugga kan aldrig synas utanför sin egen klippbana.
    // Den här fyllningen finns bara för att kasta en korrekt skugga; den täcks helt av
    // gradient-passet nedan (färgen spelar ingen roll, CREAM_LO återanvänds för att undvika en
    // ny konstant).
    CGContextSaveGState(ctx)
    CGContextSetShadowWithColor(ctx, CGSizeMake(0.0, s.SHADOW_DY.toDouble()), s.SHADOW_BLUR.toDouble(), shadowUiColor.CGColor)
    uiColor(s.CREAM_LO).setFill()
    UIBezierPath.bezierPathWithOvalInRect(CGRectMake(cx - r, cy - r, r * 2, r * 2)).fill()
    CGContextRestoreGState(ctx)

    // Radial gradient OVANPÅ den opaka fyllningen, klippt till discen — ingen skugga behövs i
    // detta pass (skuggan är redan kastad av den opaka fyllningen ovan).
    CGContextSaveGState(ctx)
    UIBezierPath.bezierPathWithOvalInRect(CGRectMake(cx - r, cy - r, r * 2, r * 2)).addClip()
    val colorSpace = CGColorSpaceCreateDeviceRGB()

    fun comps(argb: Long) = listOf(16, 8, 0).map { ((argb shr it) and 0xFF).toDouble() / 255.0 } + 1.0
    val components = (comps(s.CREAM_HI) + comps(s.CREAM_LO)).toDoubleArray()
    // gradient från (cx - r*0.25, cy - r*0.3) med radie r*1.3 — samma som Android
    components.usePinned { pinned ->
        doubleArrayOf(0.0, 1.0).usePinned { locs ->
            val gradient = CGGradientCreateWithColorComponents(colorSpace, pinned.addressOf(0), locs.addressOf(0), 2u)
            CGContextDrawRadialGradient(
                ctx,
                gradient,
                CGPointMake(cx - r * s.GRADIENT_CX_OFFSET, cy - r * s.GRADIENT_CY_OFFSET),
                0.0,
                CGPointMake(cx - r * s.GRADIENT_CX_OFFSET, cy - r * s.GRADIENT_CY_OFFSET),
                r * s.GRADIENT_RADIUS,
                kCGGradientDrawsAfterEndLocation,
            )
        }
    }
    CGContextRestoreGState(ctx)

    // Koppar-ring.
    uiColor(s.COPPER).setStroke()
    UIBezierPath
        .bezierPathWithOvalInRect(
            CGRectMake(
                cx - r + s.RING_WIDTH / 2.0,
                cy - r + s.RING_WIDTH / 2.0,
                (r - s.RING_WIDTH / 2.0) * 2,
                (r - s.RING_WIDTH / 2.0) * 2,
            ),
        ).apply { lineWidth = s.RING_WIDTH.toDouble() }
        .stroke()

    // Navy-tintad fågel, centrerad, BIRD_FRACTION av discen.
    val birdTemplate = bird.imageWithRenderingMode(UIImageRenderingModeAlwaysTemplate)
    uiColor(s.NAVY).set()
    val target = s.DISC_DIAMETER * s.BIRD_FRACTION
    val birdW = bird.size.useContents { width }
    val birdH = bird.size.useContents { height }
    val scale = target / maxOf(birdW, birdH)
    val bw = birdW * scale
    val bh = birdH * scale
    birdTemplate.drawInRect(CGRectMake(cx - bw / 2, cy - bh / 2, bw, bh))

    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return image
}
