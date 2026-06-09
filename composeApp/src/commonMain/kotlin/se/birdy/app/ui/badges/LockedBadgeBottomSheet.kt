package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badge_progress_counted
import birdy_bird_scanner.composeapp.generated.resources.badge_sheet_close
import birdy_bird_scanner.composeapp.generated.resources.badge_tier_milestone
import birdy_bird_scanner.composeapp.generated.resources.badges_locked_tooltip
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.domain.badge.BadgeTier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedBadgeBottomSheet(
    progress: LockedBadgeProgress,
    onDismiss: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val badge = progress.badge
    val hidden = progress.state is BadgeGridState.Hidden
    val displayName =
        if (hidden) stringResource(Res.string.badges_locked_tooltip) else stringResource(BadgeStringMap.nameFor(badge.id))

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PaperTop) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(78.dp)
                        .rotate(-4f)
                        .clip(CircleShape)
                        .background(SandCreme)
                        .border(3.dp, AccentCopper, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val stampText = if (progress.stampNumber > 0) "№${progress.stampNumber}" else "№"
                Text(stampText, color = AccentCopper, fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 30.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(displayName, color = TextOnCreme, fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 26.sp)

            if (!hidden) {
                Spacer(Modifier.height(4.dp))
                val cat = stringResource(BadgeStringMap.categoryLabelFor(badge.category))
                val eyebrow =
                    if (badge.category.tier == BadgeTier.MILESTONE) {
                        "$cat · ${stringResource(Res.string.badge_tier_milestone)}"
                    } else {
                        cat
                    }
                Text(
                    eyebrow.uppercase(),
                    color = MarginaliaInk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.18.em,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(BadgeStringMap.descriptionFor(badge.id)),
                    color = MarginaliaInk,
                    fontFamily = caveat,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
                val s = progress.state
                val unit = BadgeStringMap.unitFor(badge)
                if (s is BadgeGridState.InProgress && unit != null && s.target > 1) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text =
                            stringResource(
                                Res.string.badge_progress_counted,
                                s.current.toString(),
                                s.target.toString(),
                                stringResource(unit),
                            ),
                        color = TextOnCreme,
                        fontFamily = caveat,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (s.current.toFloat() / s.target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentCopper,
                        trackColor = AccentCopper.copy(alpha = 0.18f),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.badge_sheet_close), color = AccentCopper, fontFamily = caveat, fontSize = 16.sp)
            }
        }
    }
}
