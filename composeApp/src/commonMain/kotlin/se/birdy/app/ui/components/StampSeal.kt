package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.StampLocked
import se.birdy.app.ui.theme.StampLockedBg
import se.birdy.app.ui.theme.StampUnlockedBg
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

enum class StampStyle { Solid, Dashed }

sealed interface StampSealState {
    fun borderStyle(): StampStyle

    fun rotationDegrees(): Float

    data class Locked(
        val name: String?,
    ) : StampSealState {
        override fun borderStyle() = StampStyle.Dashed

        override fun rotationDegrees() = 0f
    }

    data class InProgress(
        val number: Int,
        val name: String?,
        val progressLabel: String?,
    ) : StampSealState {
        override fun borderStyle() = StampStyle.Solid

        override fun rotationDegrees() = 0f
    }

    data class Unlocked(
        val number: Int,
        val glyph: String?,
        val name: String?,
    ) : StampSealState {
        override fun borderStyle() = StampStyle.Solid

        override fun rotationDegrees() = -3f
    }
}

/**
 * Recurring stamp circle — Birdy's signature glyph. 88dp default.
 */
@Composable
fun StampSeal(
    state: StampSealState,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    onClick: (() -> Unit)? = null,
) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val borderColor =
        when (state) {
            is StampSealState.Locked -> StampLocked
            is StampSealState.InProgress -> AccentCopper.copy(alpha = 0.6f)
            is StampSealState.Unlocked -> AccentCopper
        }
    val bg =
        when (state) {
            is StampSealState.Locked -> StampLockedBg
            is StampSealState.InProgress -> StampLockedBg
            is StampSealState.Unlocked -> StampUnlockedBg
        }

    Column(
        modifier = modifier.rotate(state.rotationDegrees()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val sealModifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .let { m ->
                    when (state.borderStyle()) {
                        StampStyle.Solid ->
                            m.border(width = 2.dp, brush = SolidColor(borderColor), shape = CircleShape)
                        StampStyle.Dashed ->
                            m.dashedCircleBorder(width = 1.5.dp, color = borderColor)
                    }
                }.let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
        Box(
            modifier = sealModifier,
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (state) {
                    is StampSealState.Locked ->
                        Text(
                            text = "?",
                            color = StampLocked,
                            fontFamily = serif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            fontSize = (size.value * 0.32f).sp,
                        )
                    is StampSealState.InProgress -> {
                        Text(
                            text = "№${state.number}",
                            color = AccentCopper,
                            fontFamily = caveat,
                            fontWeight = FontWeight.Bold,
                            fontSize = (size.value * 0.16f).sp,
                        )
                        if (state.progressLabel != null) {
                            Text(
                                text = state.progressLabel,
                                color = AccentCopper,
                                fontFamily = caveat,
                                fontWeight = FontWeight.Bold,
                                fontSize = (size.value * 0.14f).sp,
                            )
                        }
                    }
                    is StampSealState.Unlocked -> {
                        Text(
                            text = "№${state.number}",
                            color = AccentCopper,
                            fontFamily = caveat,
                            fontWeight = FontWeight.Bold,
                            fontSize = (size.value * 0.16f).sp,
                        )
                        if (state.glyph != null) {
                            Text(
                                text = state.glyph,
                                color = TextOnCreme,
                                fontFamily = serif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Normal,
                                fontSize = (size.value * 0.26f).sp,
                            )
                        }
                    }
                }
            }
        }
        val name =
            when (state) {
                is StampSealState.Locked -> state.name
                is StampSealState.InProgress -> state.name
                is StampSealState.Unlocked -> state.name
            }
        if (name != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = name,
                color = if (state is StampSealState.Locked) MarginaliaInk.copy(alpha = 0.6f) else TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
            )
        }
    }
}

/** Dashed circular border. Compose has no built-in, so we draw 18 dashes via PathEffect. */
private fun Modifier.dashedCircleBorder(
    width: Dp,
    color: Color,
): Modifier =
    this.drawBehind {
        val strokeWidth = width.toPx()
        val r = (size.minDimension - strokeWidth) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val segments = 18
        val dashLen = (2f * kotlin.math.PI.toFloat() * r) / (segments * 2f)
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, dashLen), 0f)
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth, pathEffect = pathEffect),
        )
    }
