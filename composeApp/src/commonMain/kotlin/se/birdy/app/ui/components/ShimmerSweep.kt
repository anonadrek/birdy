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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun Modifier.shimmerSweep(
    durationMillis: Int = 3600,
    alpha: Float = 0.275f,
    bandFraction: Float = 0.28f,
): Modifier =
    composed {
        val transition = rememberInfiniteTransition(label = "shimmerSweep")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmerSweepProgress",
        )
        drawWithCache {
            val travel = size.width * 1.8f
            val origin = -size.width * 0.4f
            val bandWidth = size.width * bandFraction
            val x = origin + travel * progress
            val brush =
                Brush.linearGradient(
                    colorStops =
                        arrayOf(
                            0.0f to Color.White.copy(alpha = 0f),
                            0.5f to Color.White.copy(alpha = alpha),
                            1.0f to Color.White.copy(alpha = 0f),
                        ),
                    start = Offset(x, 0f),
                    end = Offset(x + bandWidth, size.height),
                )
            onDrawWithContent {
                drawContent()
                drawRect(brush = brush)
            }
        }
    }
