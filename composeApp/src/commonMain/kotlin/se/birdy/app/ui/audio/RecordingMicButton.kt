package se.birdy.app.ui.audio

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper

/**
 * Copper mic-button matching `Listen.astro` on the marketing site.
 *
 * States:
 * - [MicButtonState.Idle] — solid copper circle, white ●, tap-to-start
 * - [MicButtonState.Recording] — same circle, white ■, infinite pulse-ring, tap-to-stop
 * - [MicButtonState.RecordingDisabled] — recording but elapsed < 3s; tap ignored, no pulse
 * - [MicButtonState.Analyzing] — disabled appearance, ■, no pulse
 */
@Composable
fun RecordingMicButton(
    state: MicButtonState,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val glyph =
        when (state) {
            MicButtonState.Idle -> "●"
            MicButtonState.Recording, MicButtonState.RecordingDisabled, MicButtonState.Analyzing -> "■"
        }

    val isActive = state == MicButtonState.Recording
    val scale =
        if (isActive) {
            val transition = rememberInfiniteTransition(label = "mic-pulse")
            val s by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1200),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "mic-pulse-scale",
            )
            s
        } else {
            1f
        }

    val alpha =
        when (state) {
            MicButtonState.RecordingDisabled -> 0.5f
            MicButtonState.Analyzing -> 0.4f
            else -> 1f
        }

    val clickEnabled = state == MicButtonState.Idle || state == MicButtonState.Recording

    val cd = contentDescription
    Box(
        modifier =
            modifier
                .size(64.dp)
                .scale(scale)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = AccentCopper,
                    spotColor = AccentCopper,
                ).clip(CircleShape)
                .background(AccentCopper)
                .alpha(alpha)
                .then(if (clickEnabled) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = cd
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        // Glyph is decorative — TalkBack reads the state-driven contentDescription
        // on the outer Box instead of announcing "circle"/"black square".
        Text(
            text = glyph,
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

enum class MicButtonState {
    Idle,
    Recording,
    RecordingDisabled,
    Analyzing,
}
