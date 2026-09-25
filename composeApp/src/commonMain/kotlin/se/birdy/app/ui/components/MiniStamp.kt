package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.a11y_stamp_number
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Small stamp circle for list rows — 36dp default, embossed `№N` seal, rotated -4°.
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
    val serif = rememberDmSerifDisplay()
    val hasPhoto = !photoPath.isNullOrBlank()
    val stampCd = stringResource(Res.string.a11y_stamp_number, number)
    Box(
        modifier =
            modifier
                .size(size)
                .rotate(-4f)
                .clip(CircleShape)
                .background(AccentCopper)
                .drawBehind {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = this.size.minDimension / 2f - 2.5.dp.toPx(),
                        style = Stroke(1.dp.toPx()),
                    )
                }.clearAndSetSemantics { contentDescription = stampCd },
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
            color = TextOnHero,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontSize = (size.value * 0.3f).sp,
            style =
                if (hasPhoto) {
                    TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(0f, 1f), blurRadius = 3f))
                } else {
                    TextStyle.Default
                },
        )
    }
}
