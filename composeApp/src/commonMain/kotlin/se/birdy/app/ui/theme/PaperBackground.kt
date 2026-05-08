package se.birdy.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Field Journal paper-bg: vertical gradient PaperTop→PaperBottom + sparse
 * radial-dot texture (deterministic positions, draw-once, no animation/jank).
 *
 * Use as root bg on every redesigned screen:
 * `Box(Modifier.fillMaxSize().paperBackground()) { ... }`.
 */
fun Modifier.paperBackground(): Modifier =
    this
        .background(Brush.verticalGradient(listOf(PaperTop, PaperBottom)))
        .drawBehind { drawPaperDots() }

private fun DrawScope.drawPaperDots() {
    val w = size.width
    val h = size.height
    // 6 fixed dots — low-alpha ink-fleck feel. Positions in normalized
    // (0..1) coords so they scale across screen sizes.
    val dots = listOf(
        Triple(0.12f, 0.18f, 1.5f),
        Triple(0.78f, 0.32f, 2.2f),
        Triple(0.35f, 0.65f, 1.2f),
        Triple(0.88f, 0.78f, 1.8f),
        Triple(0.22f, 0.88f, 1.0f),
        Triple(0.55f, 0.42f, 1.4f),
    )
    val ink = Color(0x14000000) // ≈8% black
    dots.forEach { (xRel, yRel, radiusDp) ->
        drawCircle(
            color = ink,
            radius = radiusDp * density,
            center = Offset(xRel * w, yRel * h),
        )
    }
}
