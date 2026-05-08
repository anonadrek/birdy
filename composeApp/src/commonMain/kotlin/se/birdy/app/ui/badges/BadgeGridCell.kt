package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_grid_hidden
import birdy_bird_scanner.composeapp.generated.resources.badges_in_progress_pill
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme

@Composable
fun BadgeGridCell(
    progress: LockedBadgeProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHidden = progress.state == BadgeGridState.Hidden
    val label =
        if (isHidden) {
            stringResource(Res.string.badges_grid_hidden)
        } else {
            stringResource(BadgeStringMap.nameFor(progress.badge.id))
        }
    val sub: String? =
        (progress.state as? BadgeGridState.InProgress)?.let {
            stringResource(Res.string.badges_in_progress_pill, it.current.toString(), it.target.toString())
        }
    val bg =
        if (progress.state is BadgeGridState.InProgress) {
            AccentCopper.copy(alpha = 0.16f)
        } else {
            SandCreme.copy(alpha = 0.6f)
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(bg)
                .clickable(onClick = onClick)
                .padding(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SandCreme),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isHidden) "?" else "★",
                color = AccentCopperLight,
                fontSize = 22.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = TextOnCreme.copy(alpha = if (isHidden) 0.4f else 0.8f),
            fontStyle = if (isHidden) FontStyle.Italic else FontStyle.Normal,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        if (sub != null) {
            Text(
                text = sub,
                color = AccentCopper,
                fontSize = 9.sp,
                fontWeight = FontWeight.W700,
            )
        }
    }
}
