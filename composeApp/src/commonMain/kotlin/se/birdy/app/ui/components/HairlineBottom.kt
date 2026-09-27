package se.birdy.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.Hairline

/**
 * Draws a single [thickness] rule along the node's bottom edge, centered at
 * `size.height - strokePx / 2` — fully inside the node's own bounds, not straddling the
 * boundary (a stroke centered exactly on `size.height` would have half its width clipped away).
 */
fun Modifier.hairlineBottom(
    color: Color = Hairline,
    thickness: Dp = 1.dp,
): Modifier =
    drawBehind {
        val strokePx = thickness.toPx()
        val y = size.height - strokePx / 2f
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokePx)
    }
