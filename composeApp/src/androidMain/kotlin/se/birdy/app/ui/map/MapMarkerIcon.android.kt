package se.birdy.app.ui.map

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable

private const val COPPER = 0xFFA8552D.toInt() // AccentCopper
private const val NAVY = 0xFF1F3A5F.toInt() // StampNavy
private const val CREAM_HI = 0xFFF4EDDC.toInt()
private const val CREAM_LO = 0xFFE5DBC4.toInt()
private const val SHADOW = 0x66281910

/**
 * Composes the find marker: a cream wax-seal disc with a copper ring, a navy-tinted Birdy bird,
 * and a downward point whose tip marks the find. Anchor the marker at (CENTER, BOTTOM) so the
 * point tip sits on the coordinate. [bird] is the copper hero_bird silhouette (any tint works
 * since it's re-tinted via SRC_IN). Sizes are in dp via [res] density.
 */
@Suppress("MagicNumber")
fun buildBirdySealMarker(
    res: Resources,
    bird: Bitmap,
): BitmapDrawable {
    val density = res.displayMetrics.density

    fun dp(v: Float) = v * density

    val ring = dp(3f)
    val diameter = dp(46f)
    val point = dp(9f)
    val pad = dp(3f) // breathing room for the drop shadow
    val w = (diameter + pad * 2).toInt()
    val h = (diameter + point + pad * 2).toInt()

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = w / 2f
    val cy = pad + diameter / 2f
    val r = diameter / 2f

    // Downward point (drawn first, behind the disc).
    val pointPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COPPER
            setShadowLayer(dp(2f), 0f, dp(1f), SHADOW)
        }
    val path =
        Path().apply {
            moveTo(cx - dp(7f), cy + r - dp(2f))
            lineTo(cx + dp(7f), cy + r - dp(2f))
            lineTo(cx, cy + r + point)
            close()
        }
    canvas.drawPath(path, pointPaint)

    // Cream disc with a soft drop shadow.
    val discPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader =
                RadialGradient(
                    cx - r * 0.25f,
                    cy - r * 0.3f,
                    r * 1.3f,
                    CREAM_HI,
                    CREAM_LO,
                    Shader.TileMode.CLAMP,
                )
            setShadowLayer(dp(2f), 0f, dp(1f), SHADOW)
        }
    canvas.drawCircle(cx, cy, r, discPaint)

    // Copper ring.
    val ringPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ring
            color = COPPER
        }
    canvas.drawCircle(cx, cy, r - ring / 2f, ringPaint)

    // Navy-tinted bird, centered, ~60% of the disc.
    val birdPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(NAVY, PorterDuff.Mode.SRC_IN)
        }
    val target = diameter * 0.6f
    val scale = target / maxOf(bird.width, bird.height)
    val bw = bird.width * scale
    val bh = bird.height * scale
    val dst = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
    canvas.drawBitmap(bird, null, dst, birdPaint)

    return BitmapDrawable(res, bmp)
}
