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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.stats.SeasonStatsUiState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.MarginaliaInk

/**
 * Plan 6b3 T10: cumulative unique-species line chart. 12 points (Jan→Dec); the
 * line is drawn with cubic Bezier smoothing between samples and small filled
 * dots mark each point so the reader can read off integer counts. The y-axis is
 * scaled to the max observed value (with a minimum of 1 so a single-species
 * year still draws cleanly).
 *
 * 1.3.0 T12: the area under the curve is now filled with [fillColor] (defaults to a
 * 12%-alpha [AccentCopper]). The fill path reuses the exact same cubic [path] the stroke
 * is drawn with — [Path.addPath] copies it wholesale, then two `lineTo`s close it down to
 * the baseline — so the point/curve math itself is untouched, only a derived shape is
 * added. The axis rule moved off a hardcoded translucent ink to the [axisColor] token.
 *
 * 1.3.0 T12b: draw order fixed to fill → axis → line → dots (the fill used to paint over
 * the axis, drawn first). The axis is `1.dp` tall (was a raw `1f` px, ~0.33dp on xxhdpi and
 * nearly invisible together with [Hairline]'s thin paper-contrast); the line stroke and dot
 * radius are now `1.5.dp`/`2.dp` (were raw `3.5f`/`4.5f` px — ≈1.17dp/1.5dp at xxhdpi's 3x
 * density; rounded up slightly for a cleaner value that keeps today's look).
 */
@Suppress("LongParameterList") // every color/size param has a default so call sites stay short.
@Composable
fun JournalLineChart(
    points: List<SeasonStatsUiState.CumulativePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = AccentCopper,
    dotColor: Color = MarginaliaInk,
    fillColor: Color = AccentCopper.copy(alpha = 0.12f),
    axisColor: Color = Hairline,
    height: Dp = 140.dp,
    contentDescription: String? = null,
) {
    if (points.isEmpty()) return
    val semanticsModifier =
        contentDescription?.let { cd -> Modifier.semantics { this.contentDescription = cd } } ?: Modifier
    Canvas(modifier = modifier.fillMaxWidth().height(height).then(semanticsModifier)) {
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
        val fillPath =
            Path().apply {
                addPath(path)
                lineTo(coords.last().x, size.height)
                lineTo(coords.first().x, size.height)
                close()
            }
        val axisHeight = 1.dp.toPx()
        drawPath(path = fillPath, color = fillColor)
        drawRect(
            color = axisColor,
            topLeft = Offset(0f, size.height - axisHeight),
            size = Size(size.width, axisHeight),
        )
        drawPath(path = path, color = lineColor, style = Stroke(width = 1.5.dp.toPx()))
        coords.forEach { drawCircle(color = dotColor, radius = 2.dp.toPx(), center = it) }
    }
}
