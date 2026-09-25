package se.birdy.app.ui.premium

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
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
import birdy_bird_scanner.composeapp.generated.resources.premium_headline_suffix
import birdy_bird_scanner.composeapp.generated.resources.premium_lifetime_note
import birdy_bird_scanner.composeapp.generated.resources.premium_price_loading
import birdy_bird_scanner.composeapp.generated.resources.premium_purchase_failed
import birdy_bird_scanner.composeapp.generated.resources.premium_purchase_pending
import birdy_bird_scanner.composeapp.generated.resources.premium_screen_close
import birdy_bird_scanner.composeapp.generated.resources.premium_subline
import birdy_bird_scanner.composeapp.generated.resources.premium_tier_lifetime_title
import birdy_bird_scanner.composeapp.generated.resources.premium_tier_yearly_title
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.OrnamentRule
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperTop
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

    LaunchedEffect(state.purchaseCompleted) {
        if (state.purchaseCompleted) onPurchaseComplete()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .paperBackground(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PremiumHero() }
            item { OrnamentRule() }
            item { PremiumHeadline() }
            item { PremiumSubline() }
            item { Spacer(Modifier.height(10.dp)) }
            item { FreeSummarySection() }
            item { Spacer(Modifier.height(8.dp)) }
            item { PremiumDivider() }
            item { Spacer(Modifier.height(2.dp)) }
            items(premiumFeatures) { f ->
                FeatureRowC(
                    icon = f.icon,
                    title = stringResource(f.title),
                    sub = stringResource(f.sub),
                )
            }
            item { Spacer(Modifier.height(10.dp)) }
            item {
                TierCard(
                    title = stringResource(Res.string.premium_tier_yearly_title),
                    price = state.formattedYearlyPrice ?: stringResource(Res.string.premium_price_loading),
                    sub = null,
                    selected = state.selectedTier == PremiumTier.YEARLY,
                    onClick = { viewModel.selectTier(PremiumTier.YEARLY) },
                )
            }
            item {
                TierCard(
                    title = stringResource(Res.string.premium_tier_lifetime_title),
                    price = state.formattedLifetimePrice ?: stringResource(Res.string.premium_price_loading),
                    sub = null,
                    selected = state.selectedTier == PremiumTier.LIFETIME,
                    onClick = { viewModel.selectTier(PremiumTier.LIFETIME) },
                )
            }
            item {
                PrimaryCta(
                    text = stringResource(Res.string.premium_cta_primary),
                    inFlight = state.purchaseInFlight,
                    enabled = state.canPurchase,
                    onClick = { viewModel.purchase() },
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
                        color = MarginaliaInk,
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
                        color = MarginaliaInk,
                        fontSize = 13.sp,
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
                    .padding(top = 18.dp, end = 14.dp)
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.78f), CircleShape),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.premium_screen_close),
                tint = MarginaliaInk,
            )
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
                .height(300.dp),
    ) {
        AsyncImage(
            model = Res.getUri("files/premium/great-tit-hero.jpg"),
            contentDescription = null,
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
                            0.55f to Color.Transparent,
                            0.85f to PaperTop.copy(alpha = 0.55f),
                            1f to PaperTop,
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 80.dp),
        ) {
            CornerBracket(Alignment.TopStart)
            CornerBracket(Alignment.TopEnd)
            CornerBracket(Alignment.BottomStart)
            CornerBracket(Alignment.BottomEnd)
        }
    }
}

@Composable
private fun BoxScope.CornerBracket(align: Alignment) {
    val topEdge = align == Alignment.TopStart || align == Alignment.TopEnd
    val leftEdge = align == Alignment.TopStart || align == Alignment.BottomStart
    val vAlign =
        when {
            topEdge && leftEdge -> Alignment.TopStart
            topEdge && !leftEdge -> Alignment.TopEnd
            !topEdge && leftEdge -> Alignment.BottomStart
            else -> Alignment.BottomEnd
        }
    Box(modifier = Modifier.align(align).size(28.dp)) {
        Box(
            modifier =
                Modifier
                    .align(if (topEdge) Alignment.TopStart else Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color.White.copy(alpha = 0.95f)),
        )
        Box(
            modifier =
                Modifier
                    .align(vAlign)
                    .size(width = 2.5.dp, height = 28.dp)
                    .background(Color.White.copy(alpha = 0.95f)),
        )
    }
}

@Composable
private fun PremiumHeadline() {
    val plain = stringResource(Res.string.premium_headline_plain)
    val accent = stringResource(Res.string.premium_headline_accent)
    val suffix = stringResource(Res.string.premium_headline_suffix)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (suffix.isNotEmpty()) {
            // EN-style: plain + accent on line 1, suffix on line 2
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = plain,
                    fontFamily = rememberDmSerifDisplay(),
                    fontStyle = FontStyle.Italic,
                    fontSize = 28.sp,
                    color = TextOnCreme,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = accent,
                    fontFamily = rememberCaveat(),
                    fontWeight = FontWeight.W600,
                    fontSize = 34.sp,
                    color = AccentCopper,
                    modifier = Modifier.rotate(-3f),
                )
            }
            Text(
                text = suffix,
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 28.sp,
                color = TextOnCreme,
            )
        } else {
            // SV-style: plain on line 1, accent on line 2
            Text(
                text = plain,
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 28.sp,
                color = TextOnCreme,
            )
            Text(
                text = accent,
                fontFamily = rememberCaveat(),
                fontWeight = FontWeight.W600,
                fontSize = 36.sp,
                color = AccentCopper,
                modifier = Modifier.rotate(-3f),
            )
        }
    }
}

@Composable
private fun PremiumSubline() {
    Text(
        text = stringResource(Res.string.premium_subline),
        fontFamily = rememberCaveat(),
        fontSize = 15.sp,
        color = MarginaliaInk,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
    )
}

@Composable
private fun FreeSummarySection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            text = stringResource(Res.string.premium_free_eyebrow),
            fontFamily = rememberCaveat(),
            fontWeight = FontWeight.W600,
            fontSize = 15.sp,
            color = MarginaliaInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MarginaliaInk.copy(alpha = 0.05f))
                    .border(1.dp, MarginaliaInk.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FreeItem(stringResource(Res.string.premium_free_scan), Modifier.weight(1f))
                FreeItem(stringResource(Res.string.premium_free_save), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FreeItem(stringResource(Res.string.premium_free_encyclopedia), Modifier.weight(1f))
                FreeItem(stringResource(Res.string.premium_free_badges), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.FreeItem(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(16.dp).clip(CircleShape).background(MarginaliaInk),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = PaperTop, fontSize = 10.sp)
        }
        Spacer(Modifier.size(7.dp))
        Text(text, fontSize = 11.sp, color = MarginaliaInk, lineHeight = 13.sp)
    }
}

@Composable
private fun PremiumDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(AccentCopper.copy(alpha = 0.4f)))
        Text(
            text = stringResource(Res.string.premium_divider),
            fontFamily = rememberCaveat(),
            fontWeight = FontWeight.W700,
            fontSize = 15.sp,
            color = AccentCopper,
        )
        Box(Modifier.weight(1f).height(1.dp).background(AccentCopper.copy(alpha = 0.4f)))
    }
}

@Composable
private fun FeatureRowC(
    icon: PremiumFeatureIcon,
    title: String,
    sub: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(AccentCopper.copy(alpha = 0.08f))
                    .border(1.3.dp, AccentCopper.copy(alpha = 0.5f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PremiumFeatureGlyph(icon, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                color = TextOnCreme,
            )
            Text(
                text = sub,
                fontSize = 11.sp,
                color = MarginaliaInk,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
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
    val border = if (selected) BorderStroke(1.8.dp, AccentCopper) else BorderStroke(1.dp, AccentCopper.copy(alpha = 0.3f))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) AccentCopper.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.35f))
                .border(border, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    this.selected = selected
                    role = Role.RadioButton
                }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Radio button
        Box(
            modifier =
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, AccentCopper, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AccentCopper),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 17.sp,
                color = TextOnCreme,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    fontSize = 11.sp,
                    color = MarginaliaInk,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text(
            text = price,
            fontFamily = rememberCaveat(),
            fontWeight = FontWeight.W600,
            fontSize = 14.sp,
            color = AccentCopper,
        )
    }
}

@Composable
private fun PrimaryCta(
    text: String,
    inFlight: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (enabled || inFlight) AccentCopper else AccentCopper.copy(alpha = 0.45f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (inFlight) {
            CircularProgressIndicator(
                color = SandCreme,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = text,
                color = SandCreme,
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
            )
        }
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
