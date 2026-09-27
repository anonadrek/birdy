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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.premium_auto_renew_disclosure
import birdy_bird_scanner.composeapp.generated.resources.premium_cta_primary
import birdy_bird_scanner.composeapp.generated.resources.premium_divider
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_badges_sub
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_badges_title
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_export_sub
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_export_title
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_map_sub
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_map_title
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_stats_sub
import birdy_bird_scanner.composeapp.generated.resources.premium_feature_stats_title
import birdy_bird_scanner.composeapp.generated.resources.premium_free_badges
import birdy_bird_scanner.composeapp.generated.resources.premium_free_encyclopedia
import birdy_bird_scanner.composeapp.generated.resources.premium_free_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.premium_free_save
import birdy_bird_scanner.composeapp.generated.resources.premium_free_scan
import birdy_bird_scanner.composeapp.generated.resources.premium_headline_accent
import birdy_bird_scanner.composeapp.generated.resources.premium_headline_plain
import birdy_bird_scanner.composeapp.generated.resources.premium_hero_photo_label
import birdy_bird_scanner.composeapp.generated.resources.premium_kicker
import birdy_bird_scanner.composeapp.generated.resources.premium_lifetime_note
import birdy_bird_scanner.composeapp.generated.resources.premium_price_loading
import birdy_bird_scanner.composeapp.generated.resources.premium_purchase_failed
import birdy_bird_scanner.composeapp.generated.resources.premium_purchase_pending
import birdy_bird_scanner.composeapp.generated.resources.premium_screen_close
import birdy_bird_scanner.composeapp.generated.resources.premium_tier_lifetime_title
import birdy_bird_scanner.composeapp.generated.resources.premium_tier_yearly_title
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BirdyPremiumButton
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.PhotoHero
import se.birdy.app.ui.theme.BrassLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.domain.premium.PremiumTier

// Text alphas over HeroMossDeep, pinned by PremiumContrastTest — change both together.
internal const val PREMIUM_FREE_ITEM_TEXT_ALPHA = 0.75f
internal const val PREMIUM_FEATURE_SUB_ALPHA = 0.6f
internal const val PREMIUM_TIER_TITLE_ALPHA = 0.65f
internal const val PREMIUM_NOTE_ALPHA = 0.55f

@Composable
fun PremiumScreen(
    viewModel: PremiumViewModel,
    onClose: () -> Unit,
    onPurchaseComplete: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.purchaseCompleted) {
        if (state.purchaseCompleted) onPurchaseComplete()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(HeroMossDeep),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { PremiumHero() }
            item { Spacer(Modifier.height(20.dp)) }
            item { FreeSummarySection() }
            item { Spacer(Modifier.height(6.dp)) }
            item { PremiumDivider() }
            item { Spacer(Modifier.height(4.dp)) }
            items(premiumFeatures) { f ->
                FeatureRowC(
                    icon = f.icon,
                    title = stringResource(f.title),
                    sub = stringResource(f.sub),
                )
            }
            item { Spacer(Modifier.height(14.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TierCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(Res.string.premium_tier_yearly_title),
                        price = state.formattedYearlyPrice ?: stringResource(Res.string.premium_price_loading),
                        selected = state.selectedTier == PremiumTier.YEARLY,
                        onClick = { viewModel.selectTier(PremiumTier.YEARLY) },
                    )
                    TierCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(Res.string.premium_tier_lifetime_title),
                        price = state.formattedLifetimePrice ?: stringResource(Res.string.premium_price_loading),
                        selected = state.selectedTier == PremiumTier.LIFETIME,
                        onClick = { viewModel.selectTier(PremiumTier.LIFETIME) },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                BirdyPremiumButton(
                    text = stringResource(Res.string.premium_cta_primary),
                    onClick = { viewModel.purchase() },
                    enabled = state.canPurchase,
                    loading = state.purchaseInFlight,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            val purchaseNotice = state.purchaseNotice
            if (purchaseNotice != null) {
                item {
                    Text(
                        text =
                            stringResource(
                                when (purchaseNotice) {
                                    PurchaseNotice.PENDING -> Res.string.premium_purchase_pending
                                    PurchaseNotice.FAILED -> Res.string.premium_purchase_failed
                                },
                            ),
                        color = TextOnHero,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            item {
                val note =
                    when (state.selectedTier) {
                        PremiumTier.YEARLY ->
                            state.formattedYearlyPrice?.let {
                                stringResource(Res.string.premium_auto_renew_disclosure, it)
                            }
                        PremiumTier.LIFETIME -> stringResource(Res.string.premium_lifetime_note)
                    }
                if (note != null) {
                    Text(
                        text = note,
                        color = TextOnHero.copy(alpha = PREMIUM_NOTE_ALPHA),
                        fontSize = 11.5.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        IconButton(
            onClick = onClose,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 14.dp)
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.premium_screen_close),
                tint = TextOnHero,
            )
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun PremiumHero() {
    PhotoHero(
        kicker = stringResource(Res.string.premium_kicker),
        title = stringResource(Res.string.premium_headline_plain),
        titleAccent = stringResource(Res.string.premium_headline_accent),
        height = 280.dp,
        image = {
            AsyncImage(
                model = Res.getUri("files/premium/great-tit-hero.jpg"),
                contentDescription = stringResource(Res.string.premium_hero_photo_label),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
private fun FreeSummarySection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        MicroLabel(text = stringResource(Res.string.premium_free_eyebrow), color = BrassLight)
        Spacer(Modifier.height(8.dp))
        val items =
            listOf(
                stringResource(Res.string.premium_free_scan),
                stringResource(Res.string.premium_free_save),
                stringResource(Res.string.premium_free_encyclopedia),
                stringResource(Res.string.premium_free_badges),
            )
        Text(
            text = items.joinToString(" · "),
            color = TextOnHero.copy(alpha = PREMIUM_FREE_ITEM_TEXT_ALPHA),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(TextOnHero.copy(alpha = 0.12f)))
    }
}

@Composable
private fun PremiumDivider() {
    MicroLabel(
        text = stringResource(Res.string.premium_divider),
        color = BrassLight,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun FeatureRowC(
    icon: PremiumFeatureIcon,
    title: String,
    sub: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.dp, BrassLight.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PremiumFeatureGlyph(icon, tint = BrassLight)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextOnHero,
                fontWeight = FontWeight.W600,
                fontSize = 15.sp,
            )
            Text(
                text = sub,
                color = TextOnHero.copy(alpha = PREMIUM_FEATURE_SUB_ALPHA),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun TierCard(
    title: String,
    price: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) BorderStroke(1.5.dp, BrassLight) else BorderStroke(1.dp, TextOnHero.copy(alpha = 0.14f))
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = if (selected) 0.08f else 0.03f))
                .border(border, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    this.selected = selected
                    role = Role.RadioButton
                }.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = TextOnHero.copy(alpha = PREMIUM_TIER_TITLE_ALPHA),
            fontWeight = FontWeight.W600,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = price,
            fontFamily = rememberDmSerifDisplay(),
            color = TextOnHero,
            fontSize = 22.sp,
        )
    }
}

internal data class PremiumFeatureItem(
    val icon: PremiumFeatureIcon,
    val title: StringResource,
    val sub: StringResource,
)

internal val premiumFeatures =
    listOf(
        PremiumFeatureItem(PremiumFeatureIcon.MAP, Res.string.premium_feature_map_title, Res.string.premium_feature_map_sub),
        PremiumFeatureItem(PremiumFeatureIcon.EXPORT, Res.string.premium_feature_export_title, Res.string.premium_feature_export_sub),
        PremiumFeatureItem(PremiumFeatureIcon.STATS, Res.string.premium_feature_stats_title, Res.string.premium_feature_stats_sub),
        PremiumFeatureItem(PremiumFeatureIcon.BADGE, Res.string.premium_feature_badges_title, Res.string.premium_feature_badges_sub),
    )
