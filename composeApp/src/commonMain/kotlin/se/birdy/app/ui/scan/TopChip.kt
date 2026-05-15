package se.birdy.app.ui.scan

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.SandCreme

@Composable
fun TopChip(
    speciesName: String,
    confidencePct: Int?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "top-chip-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    Row(
        modifier =
            modifier
                .background(
                    color = SandCreme.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                ).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // pulse-dot
        Spacer(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(
                        color = AccentCopper.copy(alpha = pulse),
                        shape = CircleShape,
                    ),
        )
        val annotatedName =
            remember(speciesName) {
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = AccentCopper,
                            fontSize = 14.sp,
                        ),
                    ) {
                        append(speciesName)
                    }
                }
            }
        Text(text = annotatedName)
        if (confidencePct != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "$confidencePct%", color = AccentCopper)
        }
    }
}
