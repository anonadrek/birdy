package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_card_a11y
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_card_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_eyebrow_breeding
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_eyebrow_migrating
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_eyebrow_present
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.dailybird.SeasonTag

@Composable
fun DailyBirdCard(
    bird: DailyBird,
    name: String,
    heroImagePath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eyebrowText =
        stringResource(
            when (bird.seasonTag) {
                SeasonTag.BREEDING -> Res.string.daily_bird_eyebrow_breeding
                SeasonTag.PRESENT -> Res.string.daily_bird_eyebrow_present
                SeasonTag.MIGRATING -> Res.string.daily_bird_eyebrow_migrating
            },
        )
    val sectionLabel = stringResource(Res.string.daily_bird_card_eyebrow)
    val a11y = stringResource(Res.string.daily_bird_card_a11y, name, eyebrowText)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid)))
                .clickable(onClick = onClick)
                .shimmerSweep()
                .semantics { contentDescription = a11y },
    ) {
        if (heroImagePath != null) {
            AsyncImage(
                model = speciesImageUri(heroImagePath),
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
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentCopper.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "№1",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = rememberCaveat(),
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    sectionLabel,
                    color = AccentCopper,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = rememberDmSerifDisplay(),
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    eyebrowText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontFamily = rememberCaveat(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
