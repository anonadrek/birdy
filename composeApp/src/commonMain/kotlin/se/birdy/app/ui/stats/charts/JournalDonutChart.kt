package se.birdy.app.ui.stats.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.stats.SeasonStatsUiState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.BrassText
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.StampNavy
import kotlin.math.min

/**
 * Plan 6b3 T10: meteorological-season donut. 4 stroked arcs (winter / spring /
 * summer / autumn), sized proportionally to observation count. The arc-stroke
 * is hollow (donut) and each season gets a distinct paper-palette color. If
 * total is zero the chart draws an empty ring so the section is still
 * recognisable.
 *
 * 1.3.0 T12: season colors now match `SeasonStatsScreen`'s own legend swatches exactly
 * (winter/spring/summer/autumn) — see that file's `SeasonLegend`. Summer uses [BrassText]
 * rather than the literal `Brass` fill token: measured against this chart's CardPaper card
 * background, `Brass` is a razor-thin 3.02:1 (barely clears WCAG 1.4.11's 3:1 graphics
 * minimum) and drops to 2.75:1 on the app's page-level MossCreme background; [BrassText]
 * clears both with real margin (5.63:1 / 5.12:1) while still reading as the brass/mässing
 * family for "summer".
 */
@Composable
fun JournalDonutChart(
    breakdown: SeasonStatsUiState.SeasonBreakdown,
    modifier: Modifier = Modifier,
    winterColor: Color = StampNavy,
    springColor: Color = HeroMossLight,
    summerColor: Color = BrassText,
    autumnColor: Color = AccentCopper,
    emptyRingColor: Color = Hairline,
    height: Dp = 160.dp,
    strokeWidth: Dp = 22.dp,
    contentDescription: String? = null,
) {
    val semanticsModifier =
        contentDescription?.let { cd -> Modifier.semantics { this.contentDescription = cd } } ?: Modifier
    Canvas(modifier = modifier.fillMaxWidth().height(height).then(semanticsModifier)) {
        val stroke = strokeWidth.toPx()
        val ringSize = min(size.width, size.height) - stroke
        val topLeft =
            Offset(
                x = (size.width - ringSize) / 2f,
                y = (size.height - ringSize) / 2f,
            )
        val arcSize = Size(ringSize, ringSize)
        val total = breakdown.total
        if (total == 0) {
            drawArc(
                color = emptyRingColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            return@Canvas
        }
        val segments =
            listOf(
                breakdown.winter to winterColor,
                breakdown.spring to springColor,
                breakdown.summer to summerColor,
                breakdown.autumn to autumnColor,
            )
        var start = -90f
        val gap = 2f
        segments.forEach { (count, color) ->
            if (count == 0) return@forEach
            val raw = (count / total.toFloat()) * 360f
            val sweep = (raw - gap).coerceAtLeast(0f)
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            start += raw
        }
    }
}
