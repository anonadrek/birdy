package se.birdy.app.ui.audio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import se.birdy.app.ui.theme.AccentCopper
import kotlin.math.PI
import kotlin.math.sin

/**
 * Copper-accent waveform bars matching the marketing site (`Listen.astro`).
 *
 * Two layers:
 * - Idle pulse: always-running sin-wave per bar so the waveform feels alive
 *   even when the mic hears silence (rms ≈ 0). Matches the website's faked
 *   CSS `scaleY` pulse.
 * - RMS modulation: live mic energy boosted ×4 (raw RMS is 0.02-0.15 for
 *   typical conversational sound) drives extra amplitude on top of the pulse.
 *
 * When [frozen] is true (Analyzing state) the idle pulse stops and bars settle
 * to a static silhouette based on the last RMS value.
 */
@Composable
fun WaveformBars(
    rms: Float,
    frozen: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 48,
) {
    val infinite = rememberInfiniteTransition(label = "wave-pulse")
    val pulsePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "wave-pulse-phase",
    )

    // Raw mic RMS sits in 0.02-0.15 for typical sound — boost so a normal
    // bird call drives bars to near-full amplitude.
    val boostedRms = (rms * 4f).coerceIn(0f, 1f)
    val animatedRms by animateFloatAsState(
        targetValue = boostedRms,
        animationSpec = tween(durationMillis = if (frozen) 600 else 120),
        label = "wave-rms",
    )

    Row(
        modifier = modifier.height(80.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(barCount) { i ->
            val barPhase = i * 0.35f
            val pulseSin =
                if (frozen) {
                    (sin(i * 1.3f) + 1f) / 2f
                } else {
                    (sin((pulsePhase * 2f * PI + barPhase).toFloat()) + 1f) / 2f
                }
            val baseAmp = 0.2f + 0.25f * pulseSin
            val rmsBoost = animatedRms * (0.3f + 0.5f * pulseSin)
            val heightFraction = (baseAmp + rmsBoost).coerceIn(0.1f, 1f)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(heightFraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentCopper.copy(alpha = 0.85f)),
            )
        }
    }
}
