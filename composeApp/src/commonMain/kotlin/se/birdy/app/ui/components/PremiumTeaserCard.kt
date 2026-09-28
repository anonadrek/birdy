package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.BrassInk
import se.birdy.app.ui.theme.BrassLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberDmSerifDisplay

// T10b Critical 1: premiumGlow()'s default peakAlpha (0.85, tuned for light paper surfaces) would
// drop this card's text to ~1.2:1 at the glow's animated peak — a screenshot can never show it
// (infinite transitions freeze at frame 0), so PremiumTeaserCardContrastTest pins this exact
// value with pure color math. 0.10 keeps every text ≥ 5:1 with margin (reviewer measured 0.12 at
// sub 4.83/CTA 4.78, 0.15 failing).
internal const val DARK_SURFACE_GLOW_PEAK_ALPHA = 0.10f

// T10b: extracted so PremiumTeaserCardContrastTest pins the exact alpha the subtitle renders at.
internal const val PREMIUM_TEASER_SUBTITLE_ALPHA = 0.7f

/**
 * Dark-moss gradient card (rost & mässing) med mässings corner-flag. Återanvänds på
 * Arkiv- och Species Profile-sidorna för att teasa Premium-features. Konsekvent
 * visuellt språk över hela appen (1.3.0 palette lift, spec 2026-09-24 §4.1).
 *
 * Plan 6b3 T8: when [premiumActive] is true AND [exportLabel] is non-null the
 * card swaps the "Unlock" CTA for an "Export Field Journal" action that invokes
 * [onExport]. While [isExporting] the CTA renders the busy label and ignores
 * clicks. Without [exportLabel] the card always renders the unlock CTA (used by
 * non-export teasers like the species-profile insights card).
 */
@Composable
fun PremiumTeaserCard(
    title: String,
    subtitle: String,
    cornerLabel: String,
    ctaLabel: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    premiumActive: Boolean = false,
    isExporting: Boolean = false,
    exportLabel: String? = null,
    exportBusyLabel: String? = null,
    onExport: () -> Unit = {},
) {
    val showExportCta = premiumActive && exportLabel != null
    val effectiveCta =
        when {
            showExportCta && isExporting -> exportBusyLabel ?: exportLabel ?: ctaLabel
            showExportCta -> exportLabel ?: ctaLabel
            else -> ctaLabel
        }
    val onCardClick: () -> Unit =
        when {
            showExportCta && isExporting -> ({})
            showExportCta -> onExport
            else -> onUnlock
        }
    val glowModifier =
        if (showExportCta) Modifier else Modifier.premiumGlow(peakAlpha = DARK_SURFACE_GLOW_PEAK_ALPHA)
    val cardShape = RoundedCornerShape(18.dp)
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 13dp (was 9dp): a bit more clearance below the corner tag so the title
                    // never crowds it, especially once the tag grows at large font scales
                    // (T10b minor 6 — the tag switched from a fixed height to heightIn(min=...)).
                    .padding(top = 13.dp)
                    .clip(cardShape)
                    .background(Brush.linearGradient(listOf(HeroMossMid, HeroMossDeep)))
                    .clickable(enabled = !(showExportCta && isExporting), onClick = onCardClick)
                    .then(glowModifier)
                    .padding(14.dp),
        ) {
            Text(
                text = title,
                fontFamily = rememberDmSerifDisplay(),
                fontSize = 19.sp,
                color = TextOnHero,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextOnHero.copy(alpha = PREMIUM_TEASER_SUBTITLE_ALPHA),
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = effectiveCta,
                    fontWeight = FontWeight.W600,
                    fontSize = 16.sp,
                    color = if (showExportCta && isExporting) BrassLight.copy(alpha = 0.55f) else BrassLight,
                )
                // Purely decorative next to the CTA text — a second affordance would otherwise
                // double up in TalkBack's announcement (T10b Important 5).
                Text(
                    "›",
                    color = BrassLight,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W600,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .offset(x = 14.dp, y = 0.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BrassLight)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    // min, not a fixed height (T10b minor 6): 9sp clips against a fixed 18dp box
                    // at large accessibility font scales.
                    .heightIn(min = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cornerLabel,
                color = BrassInk,
                fontSize = 9.sp,
                fontWeight = FontWeight.W700,
                // T10c: without this the theme's 22sp bodyLarge line height grew the tab ~2.5dp
                // taller than its own heightIn(min = 18.dp) box implies.
                lineHeight = 12.sp,
                maxLines = 1,
                letterSpacing = 0.2.em,
            )
        }
    }
}
