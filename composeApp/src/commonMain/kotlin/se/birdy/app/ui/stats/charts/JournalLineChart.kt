package se.birdy.app.ui.stats.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.stats.SeasonStatsUiState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk

/**
 * Plan 6b3 T10: cumulative unique-species line chart. 12 points (Jan→Dec); the
 * line is drawn with cubic Bezier smoothing between samples and small filled
 * dots mark each point so the reader can read off integer counts. The y-axis is
 * scaled to the max observed value (with a minimum of 1 so a single-species
 * year still draws cleanly).
 */
@Composable
fun JournalLineChart(
    points: List<SeasonStatsUiState.CumulativePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = AccentCopper,
    dotColor: Color = MarginaliaInk,
    height: Dp = 140.dp,
) {
    if (points.isEmpty()) return
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val maxValue = (points.maxOf { it.uniqueSpeciesByEndOfMonth }).coerceAtLeast(1)
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        val coords =
            points.mapIndexed { i, p ->
                val x = i * stepX
                val y = size.height - (p.uniqueSpeciesByEndOfMonth / maxValue.toFloat()) * size.height
                Offset(x, y)
            }
        val path =
            Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 1 until coords.size) {
                    val prev = coords[i - 1]
                    val cur = coords[i]
                    val midX = (prev.x + cur.x) / 2f
                    cubicTo(
                        x1 = midX,
                        y1 = prev.y,
                        x2 = midX,
                        y2 = cur.y,
                        x3 = cur.x,
                        y3 = cur.y,
                    )
                }
            }
        drawRect(
            color = MarginaliaInk.copy(alpha = 0.35f),
            topLeft = Offset(0f, size.height - 1f),
            size = Size(size.width, 1f),
        )
        drawPath(path = path, color = lineColor, style = Stroke(width = 3.5f))
        coords.forEach { drawCircle(color = dotColor, radius = 4.5f, center = it) }
    }
}
