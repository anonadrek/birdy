package se.birdy.app.ui.onboarding.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val BAR_COUNT = 16

/**
 * 16 vertikala bars med sinus-baserad amplitud. När [active] är true rör de sig
 * via rememberInfiniteTransition; när false står de stilla på minimi-amplitud.
 */
@Composable
fun OnboardingWaveformBars(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "waveform-phase",
    )
    Canvas(modifier = modifier.height(64.dp)) {
        val totalWidth = size.width
        val barWidth = totalWidth / (BAR_COUNT * 2f)
        val gap = barWidth
        val midY = size.height / 2f
        val maxAmp = size.height * 0.4f
        val minAmp = size.height * 0.08f
        for (i in 0 until BAR_COUNT) {
            val amp =
                if (active) {
                    minAmp + abs(sin(phase + i * 0.4f)) * (maxAmp - minAmp)
                } else {
                    minAmp
                }
            val x = i * (barWidth + gap) + barWidth / 2f
            drawLine(
                color = AccentCopper,
                start = Offset(x, midY - amp),
                end = Offset(x, midY + amp),
                strokeWidth = barWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}
