package se.birdy.app.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun Crosshair(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(88.dp)) {
        val frame = Color(0xFFF0EAD8).copy(alpha = 0.55f)
        val tick = Color(0xFFF0EAD8).copy(alpha = 0.75f)
        drawRoundRect(
            color = frame,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawLine(
            color = tick,
            start = Offset(size.width / 2, size.height / 2 - 7.dp.toPx()),
            end = Offset(size.width / 2, size.height / 2 + 7.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = tick,
            start = Offset(size.width / 2 - 7.dp.toPx(), size.height / 2),
            end = Offset(size.width / 2 + 7.dp.toPx(), size.height / 2),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
