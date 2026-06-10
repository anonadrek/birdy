package se.birdy.app.ui.stats.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.stats.SeasonStatsUiState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk

/**
 * Plan 6b3 T10: month-bar chart. 12 bars across the width, the current month is
 * highlighted in MarginaliaInk while the rest use AccentCopper. Zero-height bars
 * render as nothing (the axis line still anchors them visually).
 */
@Composable
fun JournalBarChart(
    bars: List<SeasonStatsUiState.MonthBar>,
    modifier: Modifier = Modifier,
    barColor: Color = AccentCopper,
    currentMonthColor: Color = MarginaliaInk,
    height: Dp = 140.dp,
    contentDescription: String? = null,
) {
    if (bars.isEmpty()) return
    val semanticsModifier =
        contentDescription?.let { cd -> Modifier.semantics { this.contentDescription = cd } } ?: Modifier
    Canvas(modifier = modifier.fillMaxWidth().height(height).then(semanticsModifier)) {
        val maxCount = (bars.maxOfOrNull { it.observationCount } ?: 1).coerceAtLeast(1)
        val slot = size.width / bars.size
        val barWidth = slot * 0.62f
        val barInset = (slot - barWidth) / 2f
        val axisHeight = 1f
        bars.forEachIndexed { i, b ->
            val h = (b.observationCount / maxCount.toFloat()) * (size.height - axisHeight)
            val x = i * slot + barInset
            drawRect(
                color = if (b.isCurrent) currentMonthColor else barColor,
                topLeft = Offset(x, size.height - axisHeight - h),
                size = Size(barWidth, h),
            )
        }
        drawRect(
            color = MarginaliaInk.copy(alpha = 0.35f),
            topLeft = Offset(0f, size.height - axisHeight),
            size = Size(size.width, axisHeight),
        )
    }
}
