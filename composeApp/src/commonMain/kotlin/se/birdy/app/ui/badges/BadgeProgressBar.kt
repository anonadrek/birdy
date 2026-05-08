package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_screen_progress_label
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.OffwhiteWarm

@Composable
fun BadgeProgressBar(
    unlocked: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row {
            Text(
                text = "$unlocked",
                color = AccentCopperLight,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.W700,
                fontSize = 28.sp,
            )
            Text(
                text = " / $total",
                color = OffwhiteWarm.copy(alpha = 0.7f),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.W500,
                fontSize = 28.sp,
            )
        }
        Text(
            text = stringResource(Res.string.badges_screen_progress_label),
            color = OffwhiteWarm.copy(alpha = 0.85f),
            fontSize = 9.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 0.18.em,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentCopper.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = if (total == 0) 0f else unlocked / total.toFloat())
                        .height(4.dp)
                        .background(AccentCopper),
            )
        }
    }
}
