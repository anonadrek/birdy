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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

// Release 1.3.0 Task 7g item 5: the rust accent word sat straight on the photo at about 1.5:1.
// The text now sits on a scrim drawn above the photo and the glow, the accent is apricot (the
// palette's accent on dark), and PremiumHeroCardContrastTest checks these values against a
// white backdrop under the scrim, the brightest the photo or the glow's peak can make it.
// Review fix I2: the scrim is neutral black, not moss (Albin 2026-10-06: green over the bird takes
// away from it), at the lightest alpha that keeps every line at 4.5:1 (apricot ≈ 4.6:1 at 0.70).
// Same value as PhotoScrim on feature/1.3-foto-klar; switch to that token once it is merged here.
internal const val PREMIUM_HERO_TEXT_SCRIM_ALPHA = 0.70f
internal const val PREMIUM_HERO_SUBLINE_ALPHA = 0.9f
internal val PremiumHeroScrimColor = Color.Black
internal val PremiumHeroTextColor = TextOnHero
internal val PremiumHeroAccentColor = AccentCopperLight

/**
 * Settings-skärmens premium-upsell: foto + glöd, och texten (headline, rad, pill) på en neutral
 * mörk toning som följer textblocket (samma grepp som PhotoHero): den tonar in ovanför blocket och
 * ligger sedan jämn ända ner. Foto laddas via Coil från files/premium/great-tit-hero.jpg.
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
        // The glow sweeps over the photo, under the text scrim below.
        Box(modifier = Modifier.fillMaxSize().premiumGlow())
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .drawBehind {
                        drawTextScrim(fadeAbove = SCRIM_FADE_ABOVE.toPx(), padding = TEXT_BLOCK_PADDING.toPx())
                    }.padding(TEXT_BLOCK_PADDING),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = headlinePlain,
                    fontFamily = rememberDmSerifDisplay(),
                    fontStyle = FontStyle.Italic,
                    fontSize = 22.sp,
                    color = PremiumHeroTextColor,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = headlineAccent,
                    fontFamily = rememberCaveat(),
                    fontWeight = FontWeight.W600,
                    fontSize = 26.sp,
                    color = PremiumHeroAccentColor,
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
                    color = PremiumHeroTextColor.copy(alpha = PREMIUM_HERO_SUBLINE_ALPHA),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.size(8.dp))
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

private val TEXT_BLOCK_PADDING = 16.dp
private val SCRIM_FADE_ABOVE = 24.dp

/**
 * Fades in from [fadeAbove] above the text block, through its top [padding], and is a flat
 * [PREMIUM_HERO_TEXT_SCRIM_ALPHA] black from the first line of text down, so every line sits on the
 * flat part. Drawing above the block's own bounds is fine: the card's clip keeps it inside.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTextScrim(
    fadeAbove: Float,
    padding: Float,
) {
    val height = size.height + fadeAbove
    val flatFrom = ((fadeAbove + padding) / height).coerceIn(0f, 1f)
    drawRect(
        brush =
            Brush.verticalGradient(
                0f to PremiumHeroScrimColor.copy(alpha = 0f),
                flatFrom to PremiumHeroScrimColor.copy(alpha = PREMIUM_HERO_TEXT_SCRIM_ALPHA),
                1f to PremiumHeroScrimColor.copy(alpha = PREMIUM_HERO_TEXT_SCRIM_ALPHA),
                startY = -fadeAbove,
                endY = size.height,
            ),
        topLeft = Offset(0f, -fadeAbove),
        size = Size(size.width, height),
    )
}
