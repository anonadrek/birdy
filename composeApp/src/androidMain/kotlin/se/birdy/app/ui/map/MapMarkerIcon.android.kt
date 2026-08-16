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

/**
 * Composes the find marker: a cream wax-seal disc with a copper ring, a navy-tinted Birdy bird,
 * and a downward point whose tip marks the find. Anchor the marker at (CENTER, BOTTOM) so the
 * point tip sits on the coordinate. [bird] is the copper hero_bird silhouette (any tint works
 * since it's re-tinted via SRC_IN). Sizes are in dp via [res] density.
 */
fun buildBirdySealMarker(
    res: Resources,
    bird: Bitmap,
): BitmapDrawable {
    val density = res.displayMetrics.density

    fun dp(v: Float) = v * density

    val ring = dp(MapMarkerSpec.RING_WIDTH)
    val diameter = dp(MapMarkerSpec.DISC_DIAMETER)
    val point = dp(MapMarkerSpec.POINT_HEIGHT)
    val pad = dp(MapMarkerSpec.PADDING) // breathing room for the drop shadow
    val w = dp(MapMarkerSpec.markerWidth()).toInt()
    val h = dp(MapMarkerSpec.markerHeight()).toInt()

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = w / 2f
    val cy = pad + diameter / 2f
    val r = diameter / 2f

    // Downward point (drawn first, behind the disc).
    val pointPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MapMarkerSpec.COPPER.toInt()
            setShadowLayer(dp(MapMarkerSpec.SHADOW_BLUR), 0f, dp(MapMarkerSpec.SHADOW_DY), MapMarkerSpec.SHADOW.toInt())
        }
    val path =
        Path().apply {
            moveTo(cx - dp(MapMarkerSpec.POINT_HALF_WIDTH), cy + r - dp(MapMarkerSpec.POINT_TOP_INSET))
            lineTo(cx + dp(MapMarkerSpec.POINT_HALF_WIDTH), cy + r - dp(MapMarkerSpec.POINT_TOP_INSET))
            lineTo(cx, cy + r + point)
            close()
        }
    canvas.drawPath(path, pointPaint)

    // Cream disc with a soft drop shadow.
    val discPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader =
                RadialGradient(
                    cx - r * MapMarkerSpec.GRADIENT_CX_OFFSET,
                    cy - r * MapMarkerSpec.GRADIENT_CY_OFFSET,
                    r * MapMarkerSpec.GRADIENT_RADIUS,
                    MapMarkerSpec.CREAM_HI.toInt(),
                    MapMarkerSpec.CREAM_LO.toInt(),
                    Shader.TileMode.CLAMP,
                )
            setShadowLayer(dp(MapMarkerSpec.SHADOW_BLUR), 0f, dp(MapMarkerSpec.SHADOW_DY), MapMarkerSpec.SHADOW.toInt())
        }
    canvas.drawCircle(cx, cy, r, discPaint)

    // Copper ring.
    val ringPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ring
            color = MapMarkerSpec.COPPER.toInt()
        }
    canvas.drawCircle(cx, cy, r - ring / 2f, ringPaint)

    // Navy-tinted bird, centered, ~60% of the disc.
    val birdPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(MapMarkerSpec.NAVY.toInt(), PorterDuff.Mode.SRC_IN)
        }
    val target = diameter * MapMarkerSpec.BIRD_FRACTION
    val scale = target / maxOf(bird.width, bird.height)
    val bw = bird.width * scale
    val bh = bird.height * scale
    val dst = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
    canvas.drawBitmap(bird, null, dst, birdPaint)

    return BitmapDrawable(res, bmp)
}
