package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.a11y_stamp_track
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.StampLocked
import se.birdy.app.ui.theme.rememberCaveat

/**
 * 5×N grid of stamp-cells — filled (copper-fill) or empty (dashed border).
 * Used in the Badges screen hero as a visual progress indicator (replaces the
 * traditional progress bar).
 */
@Composable
fun StampTrack(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
    columns: Int = 5,
) {
    val caveat = rememberCaveat()
    val rows = (total + columns - 1) / columns
    val trackCd = stringResource(Res.string.a11y_stamp_track, filled, total)
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = trackCd },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index >= total) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {}
                    } else {
                        StampCell(
                            isFilled = index < filled,
                            number = index + 1,
                            caveatFamily = caveat,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StampCell(
    isFilled: Boolean,
    number: Int,
    caveatFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(if (isFilled) AccentCopper else Color.Transparent)
                .let { m -> if (isFilled) m else m.dashedCircleStroke(StampLocked, 1.5.dp) },
        contentAlignment = Alignment.Center,
    ) {
        if (isFilled) {
            Text(
                text = number.toString(),
                color = OffwhiteWarm,
                fontFamily = caveatFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        }
    }
}

private fun Modifier.dashedCircleStroke(
    color: Color,
    width: Dp,
): Modifier =
    this.drawBehind {
        val strokeWidth = width.toPx()
        val r = (size.minDimension - strokeWidth) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val dashLen = (2f * kotlin.math.PI.toFloat() * r) / 24f
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, dashLen), 0f)
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth, pathEffect = pathEffect),
        )
    }
