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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badge_next_tier_label
import birdy_bird_scanner.composeapp.generated.resources.badge_next_tier_value
import birdy_bird_scanner.composeapp.generated.resources.badge_tier_milestone
import birdy_bird_scanner.composeapp.generated.resources.unlock_button_dismiss
import birdy_bird_scanner.composeapp.generated.resources.unlock_label
import birdy_bird_scanner.composeapp.generated.resources.unlock_unlocked_at
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeTier

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
    stampNumber: Int? = null,
    nextTier: NextTier? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()

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
        containerColor = PaperTop,
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
                        .rotate(-4f)
                        .clip(CircleShape)
                        .background(AccentCopper)
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
                Box(
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(2.dp, PaperTop, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (stampNumber != null) "№$stampNumber" else "✦",
                        color = PaperTop,
                        fontFamily = serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = if (stampNumber != null) 22.sp else 28.sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(Res.string.unlock_label).uppercase(),
                color = AccentCopper,
                fontFamily = caveat,
                fontSize = 16.sp,
                letterSpacing = 0.08.em,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                stringResource(nameRes),
                color = TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 26.sp,
            )

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
                stringResource(descriptionRes),
                color = MarginaliaInk,
                fontFamily = caveat,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(
                    Res.string.unlock_unlocked_at,
                    formatBadgeFullDate(unlockedAt, zone, locale),
                ),
                color = HeroMossDeep,
                fontSize = 12.sp,
            )

            if (nextTier != null) {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentCopper.copy(alpha = 0.08f))
                            .border(1.dp, AccentCopper.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Text(
                        stringResource(Res.string.badge_next_tier_label).uppercase(),
                        color = AccentCopper,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 0.18.em,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(
                            Res.string.badge_next_tier_value,
                            stringResource(BadgeStringMap.nameFor(nextTier.nextBadgeId)),
                            nextTier.remaining.toString(),
                        ),
                        color = MarginaliaInk,
                        fontFamily = caveat,
                        fontSize = 18.sp,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.unlock_button_dismiss),
                    color = AccentCopper,
                    fontFamily = caveat,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
