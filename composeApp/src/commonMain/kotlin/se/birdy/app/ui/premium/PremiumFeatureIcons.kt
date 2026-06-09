package se.birdy.app.ui.premium

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper

/** De fyra premium-feature-ikonerna, ritade dependency-fritt i tunn koppar-stroke. */
enum class PremiumFeatureIcon { MAP, EXPORT, STATS, BADGE }

@Composable
fun PremiumFeatureGlyph(
    icon: PremiumFeatureIcon,
    modifier: Modifier = Modifier,
    tint: Color = AccentCopper,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            PremiumFeatureIcon.MAP -> {
                val cx = w * 0.5f
                val headCy = h * 0.36f
                val r = w * 0.28f
                drawCircle(tint, radius = r, center = Offset(cx, headCy), style = stroke)
                val tail =
                    Path().apply {
                        moveTo(cx - r * 0.72f, headCy + r * 0.72f)
                        lineTo(cx, h * 0.92f)
                        lineTo(cx + r * 0.72f, headCy + r * 0.72f)
                    }
                drawPath(tail, tint, style = stroke)
                drawCircle(tint, radius = w * 0.09f, center = Offset(cx, headCy))
            }
            PremiumFeatureIcon.EXPORT -> {
                val left = w * 0.26f
                val right = w * 0.74f
                val top = h * 0.12f
                val bottom = h * 0.88f
                val page =
                    Path().apply {
                        moveTo(left, top)
                        lineTo(right, top)
                        lineTo(right, bottom)
                        lineTo(left, bottom)
                        close()
                    }
                drawPath(page, tint, style = stroke)
                val ax = w * 0.5f
                drawLine(tint, Offset(ax, h * 0.34f), Offset(ax, h * 0.66f), strokeWidth = sw, cap = StrokeCap.Round)
                val chevron =
                    Path().apply {
                        moveTo(ax - w * 0.12f, h * 0.54f)
                        lineTo(ax, h * 0.68f)
                        lineTo(ax + w * 0.12f, h * 0.54f)
                    }
                drawPath(chevron, tint, style = stroke)
            }
            PremiumFeatureIcon.STATS -> {
                val base = h * 0.82f
                drawLine(tint, Offset(w * 0.16f, base), Offset(w * 0.84f, base), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, base), Offset(w * 0.30f, h * 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, base), Offset(w * 0.50f, h * 0.34f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.70f, base), Offset(w * 0.70f, h * 0.46f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            PremiumFeatureIcon.BADGE -> {
                val cx = w * 0.5f
                val cy = h * 0.38f
                val r = w * 0.26f
                drawCircle(tint, radius = r, center = Offset(cx, cy), style = stroke)
                val ribbons =
                    Path().apply {
                        moveTo(cx - r * 0.55f, cy + r * 0.8f)
                        lineTo(w * 0.34f, h * 0.92f)
                        lineTo(cx, h * 0.74f)
                        lineTo(w * 0.66f, h * 0.92f)
                        lineTo(cx + r * 0.55f, cy + r * 0.8f)
                    }
                drawPath(ribbons, tint, style = stroke)
            }
        }
    }
}
