package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badge_sheet_close
import birdy_bird_scanner.composeapp.generated.resources.badge_sheet_locked_body
import birdy_bird_scanner.composeapp.generated.resources.badge_sheet_progress_label
import birdy_bird_scanner.composeapp.generated.resources.badges_locked_tooltip
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedBadgeBottomSheet(
    progress: LockedBadgeProgress,
    onDismiss: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val displayName: String =
        when (progress.state) {
            BadgeGridState.Hidden -> stringResource(Res.string.badges_locked_tooltip)
            else -> stringResource(BadgeStringMap.nameFor(progress.badge.id))
        }
    val descriptionRes =
        when (progress.state) {
            BadgeGridState.Hidden -> null
            else -> BadgeStringMap.descriptionFor(progress.badge.id)
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PaperTop,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = displayName,
                color = TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(12.dp))
            when (val s = progress.state) {
                is BadgeGridState.InProgress -> {
                    val ratio = if (s.target > 0) s.current.toFloat() / s.target.toFloat() else 0f
                    val pct = (ratio.coerceIn(0f, 1f) * 100).toInt()
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentCopper,
                        trackColor = AccentCopper.copy(alpha = 0.18f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text =
                            stringResource(
                                Res.string.badge_sheet_progress_label,
                                s.current.toString(),
                                s.target.toString(),
                                "$pct%",
                            ),
                        color = MarginaliaInk,
                        fontFamily = caveat,
                        fontSize = 14.sp,
                    )
                    if (descriptionRes != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(descriptionRes),
                            color = MarginaliaInk,
                            fontFamily = caveat,
                            fontSize = 14.sp,
                        )
                    }
                }
                BadgeGridState.Locked -> {
                    Text(
                        text = stringResource(Res.string.badge_sheet_locked_body),
                        color = MarginaliaInk,
                        fontFamily = caveat,
                        fontSize = 14.sp,
                    )
                    if (descriptionRes != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(descriptionRes),
                            color = MarginaliaInk,
                            fontFamily = caveat,
                            fontSize = 14.sp,
                        )
                    }
                }
                BadgeGridState.Hidden ->
                    Text(
                        text = stringResource(Res.string.badges_locked_tooltip),
                        color = MarginaliaInk,
                        fontFamily = caveat,
                        fontSize = 14.sp,
                    )
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(
                    text = stringResource(Res.string.badge_sheet_close),
                    color = AccentCopper,
                    fontFamily = caveat,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
