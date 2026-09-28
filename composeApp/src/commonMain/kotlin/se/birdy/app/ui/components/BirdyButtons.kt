package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperDeep
import se.birdy.app.ui.theme.Brass
import se.birdy.app.ui.theme.BrassInk
import se.birdy.app.ui.theme.BrassLight
import se.birdy.app.ui.theme.TextOnHero

private val ButtonShape = RoundedCornerShape(14.dp)

// Disabled fill: dimmed but still legible against paper (not the a11y announcement — that
// comes from clickable(enabled = false), unaffected by this purely visual alpha).
private const val DISABLED_ALPHA = 0.45f

// Subtle light top line on filled buttons (mockup: inset 0 1px 0 rgba(255,255,255,.18-.35));
// one value shared by rust and brass keeps FilledButton a single, undifferentiated renderer.
private const val TOP_LINE_ALPHA = 0.18f
private val TopLineWidth = 1.dp

/** Rust = "do something". The one primary action on a screen. */
@Suppress("LongParameterList") // text/onClick/modifier + enabled/loading/leadingIcon is the full, deliberate API.
@Composable
fun BirdyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    FilledButton(
        text = text,
        onClick = onClick,
        brush = Brush.linearGradient(listOf(AccentCopper, AccentCopperDeep)),
        contentColor = TextOnHero,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        leadingIcon = leadingIcon,
    )
}

/** Brass = Premium. Only for buying / Premium actions. */
@Composable
fun BirdyPremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    FilledButton(
        text = text,
        onClick = onClick,
        brush = Brush.linearGradient(listOf(BrassLight, Brass)),
        contentColor = BrassInk,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        leadingIcon = null,
    )
}

/**
 * Secondary action: rust text, no fill. [enabled]/[loading] mirror [FilledButton]'s guards —
 * callers that gate a save/cancel flow on in-flight state (Match "Cancel", Disambig "Save as
 * unknown") need these to keep working once wired to this button.
 */
@Suppress("LongParameterList") // text/onClick/modifier/color + enabled/loading is the full, deliberate API.
@Composable
fun BirdyTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AccentCopper,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .alpha(if (enabled || loading) 1f else DISABLED_ALPHA)
                .clip(ButtonShape)
                .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Same technique as FilledButton: the label stays in the semantics tree (alpha 0)
        // while loading, so a screen reader still announces it under the spinner.
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.W600,
            fontSize = 14.sp,
            modifier = Modifier.alpha(if (loading) 0f else 1f),
        )
        if (loading) {
            CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}

@Suppress("LongParameterList") // internal shared renderer for the two filled-button variants above.
@Composable
private fun FilledButton(
    text: String,
    onClick: () -> Unit,
    brush: Brush,
    contentColor: Color,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    leadingIcon: ImageVector?,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .alpha(if (enabled || loading) 1f else DISABLED_ALPHA)
                .shadow(if (enabled || loading) 6.dp else 0.dp, ButtonShape)
                .clip(ButtonShape)
                .background(brush)
                .drawWithContent {
                    drawContent()
                    val y = TopLineWidth.toPx() / 2f
                    drawLine(
                        color = Color.White.copy(alpha = TOP_LINE_ALPHA),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = TopLineWidth.toPx(),
                    )
                }.clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label stays in the semantics tree (alpha 0) while loading, so a screen
        // reader still announces it even though only the spinner is visible. Role.Button
        // and the disabled announcement both still come from clickable() above, unchanged.
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.alpha(if (loading) 0f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(text = text, color = contentColor, fontWeight = FontWeight.W600, fontSize = 15.sp)
            }
            if (loading) {
                CircularProgressIndicator(color = contentColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    }
}
