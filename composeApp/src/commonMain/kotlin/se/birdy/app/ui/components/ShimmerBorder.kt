package se.birdy.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/**
 * A soft highlight that travels around the rounded-rect border (perimeter glow) — same
 * fade + strength as [shimmerSweep], but routed around the edge instead of sweeping
 * diagonally through the content. Pair with a matching [cornerRadius].
 */
@Composable
fun Modifier.shimmerBorder(
    durationMillis: Int = 3600,
    alpha: Float = 0.275f,
    bandFraction: Float = 0.22f,
    strokeWidth: Dp = 2.dp,
    cornerRadius: Dp = 14.dp,
): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "shimmerBorder")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmerBorderProgress",
        )
        drawWithCache {
            val stops = shimmerBorderStops(progress, alpha, bandFraction)
            val brush = Brush.sweepGradient(*stops, center = Offset(size.width / 2f, size.height / 2f))
            val sw = strokeWidth.toPx()
            // Inset the whole stroke inside the parent clip so its outer half isn't clipped away.
            val inset = sw
            val r = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2f * inset, size.height - 2f * inset),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = sw),
                )
            }
        }
    }

/**
 * Alpha for a stop at ring position [stopT] (0..1) given the band centred at [progress].
 * Linear fade to the band edges (same falloff feel as shimmerSweep) and wraps at the seam.
 */
internal fun shimmerBorderAlpha(
    stopT: Float,
    progress: Float,
    peakAlpha: Float,
    bandFraction: Float,
): Float {
    var d = abs(stopT - progress)
    d = min(d, 1f - d) // circular distance on the [0,1) ring (wraps at the seam)
    val edge = bandFraction / 2f
    return if (d >= edge) 0f else peakAlpha * (1f - d / edge)
}

private fun shimmerBorderStops(
    progress: Float,
    peakAlpha: Float,
    bandFraction: Float,
): Array<Pair<Float, Color>> {
    val n = 24
    return Array(n + 1) { i ->
        val t = i / n.toFloat()
        t to Color.White.copy(alpha = shimmerBorderAlpha(t, progress, peakAlpha, bandFraction))
    }
}
