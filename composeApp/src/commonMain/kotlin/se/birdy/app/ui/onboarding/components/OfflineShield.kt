package se.birdy.app.ui.onboarding.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper

/**
 * Ritar en "skydds-sköld"-outline runt scenens center. [progress] = 0..1 = hur
 * stor andel av path-omkretsen som ritats.
 */
@Composable
fun OfflineShield(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.42f

        // Shield-shape: rounded top, tapered bottom point
        val path =
            Path().apply {
                moveTo(cx, cy - radius)
                cubicTo(
                    cx + radius * 0.9f,
                    cy - radius,
                    cx + radius * 1.1f,
                    cy - radius * 0.2f,
                    cx + radius * 0.85f,
                    cy + radius * 0.3f,
                )
                cubicTo(
                    cx + radius * 0.5f,
                    cy + radius * 0.95f,
                    cx,
                    cy + radius,
                    cx,
                    cy + radius,
                )
                cubicTo(
                    cx,
                    cy + radius,
                    cx - radius * 0.5f,
                    cy + radius * 0.95f,
                    cx - radius * 0.85f,
                    cy + radius * 0.3f,
                )
                cubicTo(
                    cx - radius * 1.1f,
                    cy - radius * 0.2f,
                    cx - radius * 0.9f,
                    cy - radius,
                    cx,
                    cy - radius,
                )
                close()
            }

        val measure = PathMeasure().apply { setPath(path, false) }
        val totalLen = measure.length
        val drawLen = totalLen * progress.coerceIn(0f, 1f)
        val drawPath = Path()
        measure.getSegment(0f, drawLen, drawPath, true)

        drawPath(
            path = drawPath,
            color = AccentCopper,
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}
