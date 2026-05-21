package se.birdy.app.ui.audio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.MarginaliaInk
import kotlin.math.sin

/**
 * 12-bar waveform visualization driven by RMS energy.
 *
 * Each bar height is phase-shifted by [sin] to give organic movement.
 * When [frozen] is true (Analyzing state) the animation slows to settle.
 */
@Composable
fun WaveformBars(
    rms: Float,
    frozen: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 12,
) {
    val target = rms.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (frozen) 600 else 120),
        label = "wave-rms",
    )

    Row(
        modifier =
            modifier
                .height(60.dp)
                .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(barCount) { i ->
            val phase = (sin((i * 1.3f) + (animated * 6f)) + 1f) / 2f
            val heightFraction = (0.2f + 0.8f * phase * animated).coerceIn(0.1f, 1f)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(heightFraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MarginaliaInk),
            )
        }
    }
}
