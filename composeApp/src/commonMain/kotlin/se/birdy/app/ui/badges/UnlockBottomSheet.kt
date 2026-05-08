package se.birdy.app.ui.badges

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.unlock_button_dismiss
import birdy_bird_scanner.composeapp.generated.resources.unlock_label
import birdy_bird_scanner.composeapp.generated.resources.unlock_unlocked_at
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.ItalicMixedText
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockBottomSheet(
    badge: Badge,
    unlockedAt: Instant,
    isCelebration: Boolean,
    locale: Locale,
    zone: TimeZone,
    nameRes: StringResource,
    descriptionRes: StringResource,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    var animationDone by rememberSaveable(badge.id) { mutableStateOf(!isCelebration) }
    LaunchedEffect(badge.id, isCelebration) {
        if (isCelebration) {
            // Subtle 3s celebration window per Plan 5b §6.2 — no confetti.
            delay(3_000)
            animationDone = true
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "unlock-glow")
    val glowAlpha =
        if (isCelebration && !animationDone) {
            val anim by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.7f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1_500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "glow-alpha",
            )
            anim
        } else {
            0f
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MossCreme,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(SandCreme)
                        .border(4.dp, AccentCopper, CircleShape)
                        .drawBehind {
                            if (glowAlpha > 0f) {
                                drawCircle(
                                    color = AccentCopper.copy(alpha = glowAlpha),
                                    radius = size.minDimension / 1.4f,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                )
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge.id.firstOrNull()?.uppercase() ?: "★",
                    color = AccentCopper,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(20.dp))

            ItalicMixedText(
                text = stringResource(Res.string.unlock_label),
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        color = TextOnCreme,
                    ),
                italicAccent = AccentCopperLight,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(nameRes),
                fontSize = 26.sp,
                color = TextOnCreme,
                fontWeight = FontWeight.Normal,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(descriptionRes),
                fontSize = 15.sp,
                color = TextOnCreme,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text =
                    stringResource(
                        Res.string.unlock_unlocked_at,
                        formatBadgeFullDate(unlockedAt, zone, locale),
                    ),
                fontSize = 12.sp,
                color = HeroMossDeep,
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentCopper,
                        contentColor = OffwhiteWarm,
                    ),
            ) {
                Text(stringResource(Res.string.unlock_button_dismiss))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
