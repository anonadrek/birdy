package se.birdy.app.ui.premium

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.premium_cta_primary
import birdy_bird_scanner.composeapp.generated.resources.premium_cta_subtext
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_audio
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_badges
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_export
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_stats
import birdy_bird_scanner.composeapp.generated.resources.premium_headline_accent
import birdy_bird_scanner.composeapp.generated.resources.premium_headline_plain
import birdy_bird_scanner.composeapp.generated.resources.premium_screen_close
import birdy_bird_scanner.composeapp.generated.resources.premium_subline
import birdy_bird_scanner.composeapp.generated.resources.premium_tier_lifetime_price
import birdy_bird_scanner.composeapp.generated.resources.premium_tier_lifetime_title
import birdy_bird_scanner.composeapp.generated.resources.premium_tier_yearly_price
import birdy_bird_scanner.composeapp.generated.resources.premium_tier_yearly_sub
import birdy_bird_scanner.composeapp.generated.resources.premium_tier_yearly_title
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.OrnamentRule
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.domain.premium.PremiumTier

@Composable
fun PremiumScreen(
    viewModel: PremiumViewModel,
    onClose: () -> Unit,
    onPurchaseComplete: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val features =
        listOf(
            stringResource(Res.string.premium_feature_audio),
            stringResource(Res.string.premium_feature_export),
            stringResource(Res.string.premium_feature_stats),
            stringResource(Res.string.premium_feature_badges),
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .paperBackground(),
    ) {
        IconButton(
            onClick = onClose,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.premium_screen_close),
                tint = AccentCopper,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(top = 56.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PremiumHero() }
            item { PremiumHeadline() }
            item { OrnamentRule() }
            items(features) { feature -> FeatureRow(feature) }
            item { OrnamentRule() }
            item {
                TierCard(
                    title = stringResource(Res.string.premium_tier_yearly_title),
                    price = stringResource(Res.string.premium_tier_yearly_price),
                    sub = stringResource(Res.string.premium_tier_yearly_sub),
                    selected = state.selectedTier == PremiumTier.YEARLY,
                    onClick = { viewModel.selectTier(PremiumTier.YEARLY) },
                )
            }
            item {
                TierCard(
                    title = stringResource(Res.string.premium_tier_lifetime_title),
                    price = stringResource(Res.string.premium_tier_lifetime_price),
                    sub = null,
                    selected = state.selectedTier == PremiumTier.LIFETIME,
                    onClick = { viewModel.selectTier(PremiumTier.LIFETIME) },
                )
            }
            item {
                PrimaryCta(
                    text = stringResource(Res.string.premium_cta_primary),
                    inFlight = state.purchaseInFlight,
                    onClick = {
                        viewModel.purchase()
                        onPurchaseComplete()
                    },
                )
            }
            item {
                Text(
                    text = stringResource(Res.string.premium_cta_subtext),
                    fontFamily = rememberCaveat(),
                    color = MarginaliaInk,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun PremiumHero() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.5.dp, AccentCopper, RoundedCornerShape(6.dp)),
    ) {
        AsyncImage(
            model = Res.getUri("files/premium/great-tit-hero.jpg"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PremiumHeadline() {
    val plain = stringResource(Res.string.premium_headline_plain)
    val accent = stringResource(Res.string.premium_headline_accent)
    val sub = stringResource(Res.string.premium_subline)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = plain,
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 30.sp,
                color = TextOnCreme,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = accent,
                fontFamily = rememberCaveat(),
                fontWeight = FontWeight.W600,
                fontSize = 36.sp,
                color = AccentCopper,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = sub,
            fontFamily = rememberCaveat(),
            fontSize = 16.sp,
            color = MarginaliaInk,
        )
    }
}

@Composable
private fun FeatureRow(feature: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "✓",
            fontFamily = rememberCaveat(),
            fontSize = 22.sp,
            color = AccentCopper,
            fontWeight = FontWeight.W700,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = feature,
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
            fontSize = 16.sp,
            color = TextOnCreme,
        )
    }
}

@Composable
private fun TierCard(
    title: String,
    price: String,
    sub: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) BorderStroke(2.dp, AccentCopper) else BorderStroke(1.dp, AccentCopper.copy(alpha = 0.3f))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) SandCreme else Color.Transparent)
                .border(border, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                color = TextOnCreme,
            )
            Text(
                text = price,
                fontWeight = FontWeight.W600,
                fontSize = 15.sp,
                color = AccentCopper,
            )
        }
        if (sub != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = sub,
                fontFamily = rememberCaveat(),
                fontSize = 14.sp,
                color = MarginaliaInk,
            )
        }
    }
}

@Composable
private fun PrimaryCta(
    text: String,
    inFlight: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(AccentCopper)
                .clickable(enabled = !inFlight, onClick = onClick)
                .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp,
        )
    }
}
