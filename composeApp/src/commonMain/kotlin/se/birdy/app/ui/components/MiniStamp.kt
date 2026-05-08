package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.StampUnlockedBg
import se.birdy.app.ui.theme.rememberCaveat

/**
 * Small stamp circle for list rows — 36dp default, `№N` in Caveat copper, rotated -4°.
 */
@Composable
fun MiniStamp(
    number: Int,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val caveat = rememberCaveat()
    Box(
        modifier = modifier
            .size(size)
            .rotate(-4f)
            .clip(CircleShape)
            .background(StampUnlockedBg)
            .border(width = 2.dp, color = AccentCopper, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "№$number",
            color = AccentCopper,
            fontFamily = caveat,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.32f).sp,
        )
    }
}
