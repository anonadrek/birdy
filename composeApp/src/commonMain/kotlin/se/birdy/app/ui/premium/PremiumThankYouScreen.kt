package se.birdy.app.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_body
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_continue
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_headline
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_kicker
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_signoff
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.PlatformBackHandler
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat

/**
 * Shown to early (grandfathered) users: once automatically after updating to 1.3.0, and
 * instead of the purchase screen whenever they open Premium. Spec 2026-09-24 §5.2.
 */
@Composable
fun PremiumThankYouScreen(onClose: () -> Unit) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    LazyColumn(
        modifier = Modifier.fillMaxSize().paperBackground(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            JournalIntro(
                label = stringResource(Res.string.premium_thanks_kicker),
                headline = stringResource(Res.string.premium_thanks_headline),
                sub = stringResource(Res.string.premium_thanks_body),
                topPadding = 56,
            )
        }
        items(premiumFeatures) { feature ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = AccentCopper,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(text = stringResource(feature.title), color = TextOnCreme, fontSize = 16.sp)
            }
        }
        item {
            Text(
                text = stringResource(Res.string.premium_thanks_signoff),
                fontFamily = rememberCaveat(),
                color = MarginaliaInk,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            )
        }
        item {
            Text(
                text = stringResource(Res.string.premium_thanks_continue),
                color = SandCreme,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentCopper)
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(vertical = 14.dp),
            )
        }
    }
}
