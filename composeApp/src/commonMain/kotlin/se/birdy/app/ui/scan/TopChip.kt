package se.birdy.app.ui.scan

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.scan_throttle_indicator
import org.jetbrains.compose.resources.stringResource

@Composable
fun TopChip(
    speciesName: String,
    confidencePct: Int?,
    isThrottled: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "top-chip-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    Row(
        modifier =
            modifier
                .background(
                    color = Color(0xF0D8D0BC), // sand-creme @ 0.94
                    shape = RoundedCornerShape(16.dp),
                ).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // pulse-dot
        Spacer(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(
                        color = Color(0xFF8C5A3C).copy(alpha = pulse),
                        shape = CircleShape,
                    ),
        )
        Text(text = speciesName, color = Color(0xFF2A3525))
        if (confidencePct != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "$confidencePct%", color = Color(0xFF8C5A3C))
        }
        if (isThrottled) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(Res.string.scan_throttle_indicator), color = Color(0xFF5C6E48))
        }
    }
}
