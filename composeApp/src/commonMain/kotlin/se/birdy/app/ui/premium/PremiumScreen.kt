package se.birdy.app.ui.premium

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import se.birdy.app.ui.components.GlassIconButton
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.PaperSheetOverlap
import se.birdy.app.ui.components.PaperSheetTop
import se.birdy.app.ui.components.PhotoHero
import se.birdy.app.ui.components.ReportStatusBarBackdrop
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

// TierCard fill alphas — the real backdrop the tier title sits on is this fill composited over
// HeroMossDeep, not HeroMossDeep directly. Selected (more white mixed in) is the lower-contrast
// case for light text; both are pinned by PremiumContrastTest.
internal const val PREMIUM_TIER_FILL_SELECTED_ALPHA = 0.08f
internal const val PREMIUM_TIER_FILL_UNSELECTED_ALPHA = 0.03f

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

    // The whole screen is dark moss, even after scroll (spec §3 A2, §4.3).
    ReportStatusBarBackdrop(isDark = true)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(HeroMossDeep),
    ) {
        LazyColumn(
            // The bottom bar is hidden on this screen (AppScaffold); content must not end up
            // under the gesture bar or nav buttons, and the dark background now fills all the
            // way down (no paper-coloured band, Plan 3 Task 6).
            contentPadding =
                PaddingValues(
                    bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
        ) {
            item { PremiumHero() }
            // The photo ends under the moss page's rounded edge, not on a hard line (2026-10-06).
            // The edge itself leaves 24dp above the first section, like a PaperSheet's padding.
            item { PaperSheetTop(color = HeroMossDeep) }
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(IntrinsicSize.Min)
                            .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TierCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = stringResource(Res.string.premium_tier_yearly_title),
                        price = state.formattedYearlyPrice ?: stringResource(Res.string.premium_price_loading),
                        priceLoaded = state.formattedYearlyPrice != null,
                        selected = state.selectedTier == PremiumTier.YEARLY,
                        enabled = !state.purchaseInFlight,
                        onClick = { viewModel.selectTier(PremiumTier.YEARLY) },
                    )
                    TierCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = stringResource(Res.string.premium_tier_lifetime_title),
                        price = state.formattedLifetimePrice ?: stringResource(Res.string.premium_price_loading),
                        priceLoaded = state.formattedLifetimePrice != null,
                        selected = state.selectedTier == PremiumTier.LIFETIME,
                        enabled = !state.purchaseInFlight,
                        onClick = { viewModel.selectTier(PremiumTier.LIFETIME) },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            // Moved above the CTA (T9b #6): on a short screen the notice must not sit below the
            // fold, right after a tap that just changed nothing visible above it.
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
                BirdyPremiumButton(
                    text = stringResource(Res.string.premium_cta_primary),
                    onClick = { viewModel.purchase() },
                    enabled = state.canPurchase,
                    loading = state.purchaseInFlight,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
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
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        // Shared GlassIconButton (Task 11c): a real ≥48dp touch target (IconButton's own
        // effective touch target here measured ~40dp — T9c #2) holding a 36dp GlassOnPhoto disc.
        GlassIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.premium_screen_close),
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 14.dp),
        )
    }
}

@Composable
private fun PremiumHero() {
    PhotoHero(
        kicker = stringResource(Res.string.premium_kicker),
        title = stringResource(Res.string.premium_headline_plain),
        titleAccent = stringResource(Res.string.premium_headline_accent),
        // 280dp + the 24dp the moss page's rounded edge (PaperSheetTop) rides up over the photo.
        height = 304.dp,
        bottomPadding = PaperSheetOverlap + 18.dp,
        drawBehindStatusBar = true,
        image = { PremiumHeroPhoto() },
    )
}

/**
 * The one bundled Premium promo photo (`great-tit-hero.jpg`), shared by [PremiumHero] and
 * [ThanksHero] — unlike the species photos elsewhere in the app, this asset never varies, so
 * both heroes draw the exact same crop. Purely decorative: the kicker + headline already carry
 * the meaning, so `contentDescription` is null on both call sites, not a redundant caption.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun PremiumHeroPhoto() {
    AsyncImage(
        model = Res.getUri("files/premium/great-tit-hero.jpg"),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
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
            // Non-breaking spaces inside each item (not just around "·"): a bare " · " join lets
            // the line wrap inside a phrase like "839 arter", splitting the number from its unit.
            // Built in Kotlin, not strings.xml, so it stays a plain, translatable sentence there.
            text = items.joinToString("\u00A0\u00B7 ") { it.replace(' ', '\u00A0') },
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

@Suppress("LongParameterList") // title/price/priceLoaded/selected/enabled/onClick/modifier — the full, deliberate API.
@Composable
private fun TierCard(
    title: String,
    price: String,
    priceLoaded: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) BorderStroke(1.5.dp, BrassLight) else BorderStroke(1.dp, TextOnHero.copy(alpha = 0.14f))
    val fillAlpha = if (selected) PREMIUM_TIER_FILL_SELECTED_ALPHA else PREMIUM_TIER_FILL_UNSELECTED_ALPHA
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = fillAlpha))
                .border(border, RoundedCornerShape(14.dp))
                // selectable() already marks this node selected/RadioButton and a merge boundary
                // for TalkBack — no separate manual semantics{} block needed on top of it.
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title.uppercase(),
                color = TextOnHero.copy(alpha = PREMIUM_TIER_TITLE_ALPHA),
                fontWeight = FontWeight.W600,
                fontSize = 11.sp,
                // Always, not just when selected (T9c #4): the check glyph's own position doesn't
                // move, so a fixed end-padding avoids a layout shift when a card becomes selected.
                modifier = Modifier.padding(end = 20.dp),
            )
            Spacer(Modifier.height(6.dp))
            if (priceLoaded) {
                BasicText(
                    text = price,
                    // fontSize is NOT optional here (T9c #1): the Row above is
                    // Modifier.height(IntrinsicSize.Min), and that intrinsic-height pass measures
                    // this line at its OWN style's fontSize, ignoring autoSize entirely (autoSize
                    // only applies once real layout constraints — including the height this pass
                    // produces — are known). Without an explicit size here it fell back to
                    // ~14sp, so the row was sized for a 14sp price line and autoSize's own
                    // height-fit check then had nowhere to grow into, capping every price at the
                    // 14sp floor even when there was room for the full 22sp. Setting it to the
                    // target maxFontSize makes the intrinsic pass budget the right height, and
                    // autoSize still steps down from there for width (e.g. long/narrow prices).
                    style = TextStyle(fontFamily = rememberDmSerifDisplay(), color = TextOnHero, fontSize = 22.sp),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp),
                )
            } else {
                // Sans, smaller and fixed-size (no autosize needed): "Hämtar pris…"/"Loading
                // price…" is short and must never wrap onto a second line. Ellipsis as a last-
                // resort guard, not the expected outcome, for a future longer loading string.
                Text(
                    text = price,
                    color = TextOnHero,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = BrassLight,
                modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
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
