package se.birdy.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.ml.ZoomState
import se.birdy.ml.zoomPresets
import kotlin.math.abs

/**
 * Rad med zoom-preset-chips ovanpå live-kameran. Aktivt chip = det vars värde
 * ligger närmast nuvarande ratio (CameraX kan landa på t.ex. 1.97x). Renderar
 * inget om kameran saknar zoom (maxRatio <= 1).
 */
@Composable
fun ZoomChips(
    zoom: ZoomState,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = zoomPresets(zoom.maxRatio)
    if (presets.isEmpty()) return

    // Det preset vars värde ligger närmast nuvarande ratio markeras som aktivt.
    val active = presets.minByOrNull { abs(it - zoom.ratio) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            val isActive = preset == active
            Text(
                text = formatPreset(preset),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                modifier =
                    Modifier
                        .clickable { onSelect(preset) }
                        .background(
                            color = if (isActive) AccentCopper else Color.Black.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(50),
                        ).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun formatPreset(value: Float): String {
    val whole = value.toInt()
    val label = if (value == whole.toFloat()) whole.toString() else value.toString()
    return label + "×" // "1×"
}
