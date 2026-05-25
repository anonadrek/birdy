package se.birdy.app.ui.onboarding.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Animerar 0 → [target] när [trigger] flippar till true. Använder DM Serif Italic.
 */
@Composable
fun StreakCounter(
    target: Int,
    trigger: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 48.sp,
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            animated.snapTo(0f)
            animated.animateTo(target.toFloat(), tween(durationMillis = 800))
        } else {
            animated.snapTo(0f)
        }
    }
    val display = animated.value.toInt()
    Text(
        text = "🔥 $display",
        color = AccentCopper,
        fontFamily = rememberDmSerifDisplay(),
        fontStyle = FontStyle.Italic,
        fontSize = fontSize,
        modifier = modifier,
    )
}
