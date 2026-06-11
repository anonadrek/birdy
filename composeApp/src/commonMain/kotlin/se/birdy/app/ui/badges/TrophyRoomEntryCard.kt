package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_a11y
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_a11y_one
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_count
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_count_one
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_empty
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_eyebrow
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.shimmerSweep
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@OptIn(ExperimentalResourceApi::class)
@Composable
fun TrophyRoomEntryCard(
    hero: BadgeWithUnlock?,
    unlockedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val eyebrow = stringResource(Res.string.trophy_room_entry_eyebrow)
    val heroName = hero?.let { stringResource(BadgeStringMap.nameFor(it.badge.id)) }
    val countLabel =
        when {
            unlockedCount == 0 -> stringResource(Res.string.trophy_room_entry_empty)
            unlockedCount == 1 -> stringResource(Res.string.trophy_room_entry_count_one, "1")
            else -> stringResource(Res.string.trophy_room_entry_count, unlockedCount.toString())
        }
    val a11y =
        if (unlockedCount == 1) {
            stringResource(Res.string.trophy_room_entry_a11y_one, "1")
        } else {
            stringResource(Res.string.trophy_room_entry_a11y, unlockedCount.toString())
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid)))
                .clickable(onClick = onClick)
                .shimmerSweep(durationMillis = 6000, alpha = 0.20f)
                .semantics { contentDescription = a11y },
    ) {
        AsyncImage(
            model = Res.getUri("files/branding/trophy_hero.webp"),
            contentDescription = null,
            modifier =
                Modifier
                    .matchParentSize()
                    .blur(radius = 6.dp),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.55f),
                            ),
                        ),
                    ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (hero != null) AccentCopper else AccentCopper.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = hero?.let { "№${it.stampNumber}" } ?: "✦",
                    color = Color.White,
                    fontFamily = caveat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eyebrow,
                    color = OffwhiteWarm.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 1.6.sp,
                )
                Text(
                    text = heroName ?: countLabel,
                    color = OffwhiteWarm,
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (heroName != null) {
                    Text(
                        text = countLabel,
                        color = OffwhiteWarm.copy(alpha = 0.85f),
                        fontFamily = caveat,
                        fontSize = 14.sp,
                    )
                }
            }
            Text(
                text = "›",
                color = OffwhiteWarm,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 22.sp,
            )
        }
    }
}
