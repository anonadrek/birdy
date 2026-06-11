package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.premium_hero_chip
import birdy_bird_scanner.composeapp.generated.resources.premium_hero_photo_label
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Settings-skärmens premium-upsell. 16:9 foto med svart gradient + headline + pill.
 * Foto laddas via Coil från files/premium/great-tit-hero.jpg.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun PremiumHeroCard(
    headlinePlain: String,
    headlineAccent: String,
    subline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipLabel = stringResource(Res.string.premium_hero_chip)
    val heroPhotoLabel = stringResource(Res.string.premium_hero_photo_label)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = Res.getUri("files/premium/great-tit-hero.jpg"),
            contentDescription = heroPhotoLabel,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color.Black.copy(alpha = 0.0f),
                            1f to Color.Black.copy(alpha = 0.7f),
                        ),
                    ).premiumGlow(),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = headlinePlain,
                    fontFamily = rememberDmSerifDisplay(),
                    fontStyle = FontStyle.Italic,
                    fontSize = 22.sp,
                    color = Color.White,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = headlineAccent,
                    fontFamily = rememberCaveat(),
                    fontWeight = FontWeight.W600,
                    fontSize = 26.sp,
                    color = AccentCopper,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subline,
                    fontFamily = rememberCaveat(),
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = chipLabel,
                        fontFamily = rememberCaveat(),
                        fontWeight = FontWeight.W600,
                        fontSize = 14.sp,
                        color = AccentCopper,
                    )
                }
            }
        }
    }
}
