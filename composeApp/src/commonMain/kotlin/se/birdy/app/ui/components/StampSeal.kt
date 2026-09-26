package se.birdy.app.ui.components

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.stamp_in_progress_label
import birdy_bird_scanner.composeapp.generated.resources.stamp_locked_label
import birdy_bird_scanner.composeapp.generated.resources.stamp_unlocked_label
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Brass
import se.birdy.app.ui.theme.BrassInk
import se.birdy.app.ui.theme.BrassText
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.StampLocked
import se.birdy.app.ui.theme.StampLockedBg
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberDmSerifDisplay

// A fill this light or lighter needs dark ink text/ring content to stay legible — measured:
// Brass ≈0.28, AccentCopper ≈0.11, StampNavy ≈0.04 (WCAG relative luminance).
private const val LUMINANCE_THRESHOLD_FOR_DARK_FILL = 0.2f

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
    accentColor: Color = AccentCopper,
    onClick: (() -> Unit)? = null,
) {
    val serif = rememberDmSerifDisplay()

    val badgeName =
        when (state) {
            is StampSealState.Locked -> state.name.orEmpty()
            is StampSealState.InProgress -> state.name.orEmpty()
            is StampSealState.Unlocked -> state.name.orEmpty()
        }
    val semanticsLabel =
        when (state) {
            is StampSealState.Locked ->
                stringResource(Res.string.stamp_locked_label, badgeName)
            is StampSealState.InProgress ->
                stringResource(Res.string.stamp_in_progress_label, badgeName, state.progressLabel.orEmpty())
            is StampSealState.Unlocked ->
                stringResource(Res.string.stamp_unlocked_label, badgeName)
        }

    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {
                    contentDescription = semanticsLabel
                    if (onClick != null) role = Role.Button
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Embossed wax seal (spec 2026-09-24 §4.3): solid fill, top-left highlight + inner
        // ring for Unlocked, soft drop shadow, DM Serif Display ITALIC for the seal glyphs.
        // textOnSeal is decided by the FILL's luminance, not by an == Brass equality check, so
        // any future light accentColor automatically gets legible (dark) glyph/ring content
        // instead of silently repeating Brass's old dark-ring-on-light-fill bug.
        val textOnSeal = if (accentColor.luminance() > LUMINANCE_THRESHOLD_FOR_DARK_FILL) BrassInk else TextOnHero
        val sealModifier =
            Modifier
                .rotate(state.rotationDegrees())
                .size(size)
                .let { m -> if (state is StampSealState.Unlocked) m.shadow(3.dp, CircleShape) else m }
                .clip(CircleShape)
                .drawBehind {
                    val r = this.size.minDimension / 2f
                    when (state) {
                        is StampSealState.Unlocked -> {
                            drawCircle(accentColor)
                            drawCircle(
                                brush =
                                    Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                                        center = Offset(x = this.size.width * 0.36f, y = this.size.height * 0.32f),
                                        radius = r * 0.62f,
                                    ),
                                radius = r * 0.62f,
                                center = Offset(x = this.size.width * 0.36f, y = this.size.height * 0.32f),
                            )
                            // Inner ring is always a light hairline on the fill (mockup) — never
                            // the dark textOnSeal, which on a light fill (e.g. Brass) used to
                            // paint a near-invisible dark ring instead of the intended highlight.
                            drawCircle(
                                color = Color.White.copy(alpha = 0.4f),
                                radius = r - 4.dp.toPx(),
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                        is StampSealState.InProgress -> drawCircle(CardPaper)
                        is StampSealState.Locked -> drawCircle(StampLockedBg)
                    }
                }.let { m ->
                    when (state) {
                        is StampSealState.InProgress ->
                            m.border(width = 1.5.dp, color = accentColor.copy(alpha = 0.6f), shape = CircleShape)
                        is StampSealState.Locked -> m.dashedCircleBorder(width = 1.5.dp, color = StampLocked)
                        is StampSealState.Unlocked -> m
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
                            fontSize = (size.value * 0.32f).sp,
                        )
                    is StampSealState.InProgress -> {
                        // Brass text on the CardPaper fill is 3.0:1 (fails AA) — the border
                        // stays Brass (above), but the label swaps to the text-safe BrassText.
                        val labelColor = if (accentColor == Brass) BrassText else accentColor
                        Text(
                            text = "№${state.number}",
                            color = labelColor,
                            fontFamily = serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = (size.value * 0.18f).sp,
                        )
                        if (state.progressLabel != null) {
                            Text(
                                text = state.progressLabel,
                                color = labelColor,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.W600,
                                fontSize = (size.value * 0.12f).sp,
                            )
                        }
                    }
                    is StampSealState.Unlocked -> {
                        Text(
                            text = "№${state.number}",
                            color = textOnSeal,
                            fontFamily = serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = (size.value * 0.2f).sp,
                        )
                        if (state.glyph != null) {
                            Text(
                                text = state.glyph,
                                color = textOnSeal,
                                fontFamily = serif,
                                fontStyle = FontStyle.Italic,
                                fontSize = (size.value * 0.24f).sp,
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
                color = if (state is StampSealState.Locked) InkMuted else TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Normal,
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
