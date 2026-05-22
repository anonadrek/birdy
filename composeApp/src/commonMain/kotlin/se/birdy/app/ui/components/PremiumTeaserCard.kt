package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Paper-edge-card med corner-flag + Caveat-italic CTA-rad. Återanvänds på
 * Arkiv-, Species Profile- och (eventuellt) Settings-sidorna för att teasa
 * Premium-features. Konsekvent visuellt språk över hela appen.
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
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SandCreme)
                    .border(1.dp, AccentCopper.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .clickable(enabled = !(showExportCta && isExporting), onClick = onCardClick)
                    .padding(14.dp),
        ) {
            Text(
                text = title,
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                color = TextOnCreme,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MarginaliaInk,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = effectiveCta,
                    fontFamily = rememberCaveat(),
                    fontWeight = FontWeight.W600,
                    fontSize = 16.sp,
                    color = if (showExportCta && isExporting) AccentCopper.copy(alpha = 0.55f) else AccentCopper,
                )
                Text("›", color = AccentCopper, fontSize = 20.sp, fontWeight = FontWeight.W600)
            }
        }
        Box(
            modifier =
                Modifier
                    .offset(x = 14.dp, y = 0.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentCopper)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .height(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cornerLabel,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 0.2.em,
            )
        }
    }
}
