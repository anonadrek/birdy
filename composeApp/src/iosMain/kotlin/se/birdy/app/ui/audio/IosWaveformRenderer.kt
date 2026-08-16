package se.birdy.app.ui.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.stringByDeletingLastPathComponent
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import kotlin.math.abs

/**
 * iOS-spegel av [AndroidWaveformRenderer]s PNG-rendering: 600×200, 120 buckets,
 * 3-tap-utjämning, Mossbädd-paletten (PaperBg-bakgrund #EFE7D6, MarginaliaInk-staplar
 * #3F4F30, AccentCopper-underlinje #A8552D vid y = height-6, staplar ±40 % av höjden,
 * min-stapel 2 px). [encodeOpus] returnerar null — den dokumenterade degrade-vägen
 * (i3-spec B1; iOS har ingen system-Opus-encoder och .opus kan ändå inte spelas nativt).
 */
class IosWaveformRenderer : WaveformRendererApi {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun renderWaveformPng(
        pcm: ShortArray,
        outPath: String,
    ): String =
        withContext(Dispatchers.Default) {
            val width = 600
            val height = 200
            val buckets = 120
            val samplesPerBucket = if (pcm.isNotEmpty()) pcm.size / buckets else 1

            val peaks =
                FloatArray(buckets) { b ->
                    var max = 0
                    val start = b * samplesPerBucket
                    val end = (start + samplesPerBucket).coerceAtMost(pcm.size)
                    for (i in start until end) {
                        val v = abs(pcm[i].toInt())
                        if (v > max) max = v
                    }
                    max / 32768f
                }
            val smoothed =
                FloatArray(buckets) { i ->
                    val prev = peaks[(i - 1).coerceAtLeast(0)]
                    val cur = peaks[i]
                    val next = peaks[(i + 1).coerceAtMost(buckets - 1)]
                    (prev + cur + next) / 3f
                }

            // CF-minneshantering: CGBitmapContextCreate/CGBitmapContextCreateImage/
            // CGColorSpaceCreateDeviceRGB returnerar +1-referenser som K/N INTE hanterar
            // automatiskt (samma mönster som IosScanFramePersist.kt:s encodeBgraFrameToJpeg)
            // — explicit release i finally, nästlat yttre-till-inre i skapandeordning.
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            try {
                val ctx =
                    CGBitmapContextCreate(
                        data = null,
                        width = width.convert(),
                        height = height.convert(),
                        bitsPerComponent = 8.convert(),
                        bytesPerRow = 0.convert(),
                        space = colorSpace,
                        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
                    ) ?: error("CGBitmapContextCreate failed")
                try {
                    // OBS: CG har origo nere-vänster (y upp); Android-mallen räknar uppifrån
                    // (y ner). Vi speglar y-koordinaterna (yCg = height - yAndroid - rectHeight)
                    // så PNG:n blir visuellt identisk med Android-renderern.
                    // PaperBg #EFE7D6
                    CGContextSetRGBFillColor(ctx, 0xEF / 255.0, 0xE7 / 255.0, 0xD6 / 255.0, 1.0)
                    CGContextFillRect(ctx, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))

                    // MarginaliaInk #3F4F30 — staplar kring mittlinjen (symmetriska kring
                    // centerY → ingen y-spegling behövs, samma span i båda koordinatsystemen).
                    CGContextSetRGBFillColor(ctx, 0x3F / 255.0, 0x4F / 255.0, 0x30 / 255.0, 1.0)
                    val barWidth = width.toDouble() / buckets
                    val centerY = height / 2.0
                    val maxHalfHeight = height * 0.4
                    smoothed.forEachIndexed { i, level ->
                        val h = (maxHalfHeight * level).coerceAtLeast(2.0)
                        val x = i * barWidth
                        CGContextFillRect(ctx, CGRectMake(x + 1.0, centerY - h, barWidth - 2.0, h * 2.0))
                    }

                    // AccentCopper #A8552D — underlinje vid Android-y (height-6, 2px stroke →
                    // Android-span [193,195]). Speglad: yCg = 200-193-2 = 5 → CG-span [5,7],
                    // dvs. nära CG-golvet (y=0) = nära botten av bilden, precis som Android.
                    CGContextSetRGBFillColor(ctx, 0xA8 / 255.0, 0x55 / 255.0, 0x2D / 255.0, 1.0)
                    CGContextFillRect(ctx, CGRectMake(0.0, 5.0, width.toDouble(), 2.0))

                    val cgImage = CGBitmapContextCreateImage(ctx) ?: error("CGBitmapContextCreateImage failed")
                    try {
                        val image = UIImage.imageWithCGImage(cgImage)
                        val png = UIImagePNGRepresentation(image) ?: error("UIImagePNGRepresentation failed")

                        val parent = parentDirectory(outPath)
                        NSFileManager.defaultManager.createDirectoryAtPath(
                            parent,
                            withIntermediateDirectories = true,
                            attributes = null,
                            error = null,
                        )
                        check(png.writeToFile(outPath, atomically = true)) { "PNG write failed: $outPath" }
                        outPath
                    } finally {
                        CGImageRelease(cgImage)
                    }
                } finally {
                    CGContextRelease(ctx)
                }
            } finally {
                CGColorSpaceRelease(colorSpace)
            }
        }

    override suspend fun encodeOpus(
        pcm: ShortArray,
        outPath: String,
    ): String? = null
}

/**
 * Kotlin `String`/`NSString` är samma runtime-typ i K/N men olika nominella klasser — en
 * `as NSString`-cast inline i ett större uttryck gör att kompilatorns typinferens för de
 * omslutande generiska blocken (withContext/try) inte kan enas om övre gräns (varning:
 * "incompatible upper bounds: NSString, String … will become an error in a future release").
 * Egen funktion med explicit returtyp isolerar casten från den yttre inferensen.
 */
@Suppress("CAST_NEVER_SUCCEEDS")
private fun parentDirectory(path: String): String = (path as NSString).stringByDeletingLastPathComponent
