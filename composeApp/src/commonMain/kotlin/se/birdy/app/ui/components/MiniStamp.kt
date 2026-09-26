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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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

// Number font size as a fraction of the stamp's own dp size, stepped down by digit count so
// "№1234" (a 4-digit observation number) still fits a 36dp stamp and "№839" a 26dp one —
// unchanged from before for the overwhelmingly common 1-2 digit case.
private const val FONT_FACTOR_UP_TO_TWO_DIGITS = 0.30f
private const val FONT_FACTOR_THREE_DIGITS = 0.25f
private const val FONT_FACTOR_FOUR_OR_MORE_DIGITS = 0.21f
private const val THREE_DIGITS = 3

private fun fontFactorFor(digitCount: Int): Float =
    when {
        digitCount <= 2 -> FONT_FACTOR_UP_TO_TWO_DIGITS
        digitCount == THREE_DIGITS -> FONT_FACTOR_THREE_DIGITS
        else -> FONT_FACTOR_FOUR_OR_MORE_DIGITS
    }

/**
 * Small stamp circle for list rows — 36dp default, embossed `№N` seal, rotated -4°.
 * When [photoPath] is given, the find's photo fills the circle as a backdrop (with a dark
 * scrim for legibility) and the number renders in white on top.
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
    // sp = target-dp / fontScale, so the RENDERED size (sp * fontScale) stays pinned to a fixed
    // dp size regardless of the system's accessibility font scale — this tiny badge must fit
    // its fixed circle, unlike normal body text which should grow with font scale.
    val fontScale = LocalDensity.current.fontScale
    val targetDp = size.value * fontFactorFor(number.toString().length)
    val fontSizeSp = (targetDp / fontScale).sp
    Box(
        modifier =
            modifier
                .size(size)
                .rotate(-4f)
                .clip(CircleShape)
                .background(AccentCopper)
                // drawWithContent (not drawBehind): the ring must stay on top of the photo
                // variant's AsyncImage below, not get covered by it.
                .drawWithContent {
                    drawContent()
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
            fontSize = fontSizeSp,
            maxLines = 1,
            softWrap = false,
            style =
                if (hasPhoto) {
                    TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(0f, 1f), blurRadius = 3f))
                } else {
                    TextStyle.Default
                },
        )
    }
}
