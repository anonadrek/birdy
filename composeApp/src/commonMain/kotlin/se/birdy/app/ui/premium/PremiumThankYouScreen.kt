package se.birdy.app.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_body
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_continue
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_headline
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_kicker
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_signoff
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BirdyPremiumButton
import se.birdy.app.ui.components.HeadlineSegment
import se.birdy.app.ui.components.PaperSheetOverlap
import se.birdy.app.ui.components.PaperSheetTop
import se.birdy.app.ui.components.PhotoHero
import se.birdy.app.ui.components.PlatformBackHandler
import se.birdy.app.ui.components.ReportStatusBarBackdrop
import se.birdy.app.ui.components.parseJournalHeadline
import se.birdy.app.ui.theme.BrassLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberCaveat

// Text alpha over HeroMossDeep, pinned by PremiumContrastTest — change both together.
internal const val PREMIUM_THANKS_BODY_ALPHA = 0.85f

/**
 * Shown to early (grandfathered) users: once automatically after updating to 1.3.0, and
 * instead of the purchase screen whenever they open Premium. Spec 2026-09-24 §5.2.
 */
@Composable
fun PremiumThankYouScreen(onClose: () -> Unit) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    // The whole screen is dark moss, even after scroll (spec §5.2).
    ReportStatusBarBackdrop(isDark = true)
    val (plain, accent) = thanksHeadlineParts(stringResource(Res.string.premium_thanks_headline))
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(HeroMossDeep),
        // The bottom bar is hidden on this screen; content must not end up under the gesture
        // bar or nav buttons, and the dark background now fills all the way down (Plan 3 Task 6).
        contentPadding =
            PaddingValues(bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        item {
            ThanksHero(
                kicker = stringResource(Res.string.premium_thanks_kicker),
                plain = plain,
                accent = accent,
            )
        }
        // The photo ends under the moss page's rounded edge, not on a hard line (2026-10-06).
        // The edge itself leaves 24dp above the body, like a PaperSheet's padding.
        item { PaperSheetTop(color = HeroMossDeep) }
        item {
            Text(
                text = stringResource(Res.string.premium_thanks_body),
                color = TextOnHero.copy(alpha = PREMIUM_THANKS_BODY_ALPHA),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            )
        }
        items(premiumFeatures) { feature -> ThanksFeatureRow(feature.title) }
        item { ThanksSignoff() }
        item {
            BirdyPremiumButton(
                text = stringResource(Res.string.premium_thanks_continue),
                onClick = onClose,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/**
 * `*accent*` markup in [premium_thanks_headline] split into [PhotoHero]'s title/titleAccent
 * slots. The Accent segment(s), if any, MUST be trailing (after all Plain text) — [PhotoHero]
 * only supports one accent run, appended after the title with a space, so a `*accent*` placed
 * mid-sentence in the source string would visually reorder to the end here, not render in place.
 * Internal so [PremiumThankYouHeadlineTest] can pin this contract.
 */
internal fun thanksHeadlineParts(text: String): Pair<String, String?> {
    val segments = parseJournalHeadline(text)
    val plain = segments.filterIsInstance<HeadlineSegment.Plain>().joinToString("") { it.text }.trim()
    val accent = segments.filterIsInstance<HeadlineSegment.Accent>().joinToString(" ") { it.text }.ifBlank { null }
    return plain to accent
}

@Composable
private fun ThanksHero(
    kicker: String,
    plain: String,
    accent: String?,
) {
    PhotoHero(
        kicker = kicker,
        title = plain,
        titleAccent = accent,
        height = 280.dp,
        bottomPadding = PaperSheetOverlap + 18.dp,
        drawBehindStatusBar = true,
        image = { PremiumHeroPhoto() },
    )
}

@Composable
private fun ThanksFeatureRow(title: StringResource) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = BrassLight,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = stringResource(title), color = TextOnHero, fontSize = 15.sp)
    }
}

@Composable
private fun ThanksSignoff() {
    Text(
        text = stringResource(Res.string.premium_thanks_signoff),
        fontFamily = rememberCaveat(),
        color = BrassLight,
        fontSize = 22.sp,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
    )
}
