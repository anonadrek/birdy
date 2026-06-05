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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.StampUnlockedBg
import se.birdy.app.ui.theme.rememberCaveat

/**
 * Small stamp circle for list rows — 36dp default, `№N` in Caveat copper, rotated -4°.
 * When [photoPath] is given, the find's photo fills the circle as a blurred backdrop
 * (with a dark scrim for legibility) and the number renders in white on top.
 */
@Composable
fun MiniStamp(
    number: Int,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    photoPath: String? = null,
) {
    val caveat = rememberCaveat()
    val hasPhoto = !photoPath.isNullOrBlank()
    Box(
        modifier =
            modifier
                .size(size)
                .rotate(-4f)
                .clip(CircleShape)
                .background(StampUnlockedBg)
                .border(width = 2.dp, color = AccentCopper, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (hasPhoto) {
            AsyncImage(
                model = "file://$photoPath",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        Text(
            text = "№$number",
            color = if (hasPhoto) Color.White else AccentCopper,
            fontFamily = caveat,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.32f).sp,
            style =
                if (hasPhoto) {
                    TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(0f, 1f), blurRadius = 3f))
                } else {
                    TextStyle.Default
                },
        )
    }
}
