package se.birdy.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_caption
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_count
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_cta
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_title
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_badge
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.premiumGlow
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@Composable
fun MapPremiumTeaser(
    viewModel: MapViewModel,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(
        modifier = modifier.fillMaxSize().paperBackground().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 9.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SandCreme)
                        .border(1.dp, AccentCopper.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                        .premiumGlow()
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(Res.string.map_teaser_title),
                    textAlign = TextAlign.Center,
                    fontFamily = rememberDmSerifDisplay(),
                    fontStyle = FontStyle.Italic,
                    fontSize = 24.sp,
                    color = TextOnCreme,
                )
                Text(
                    stringResource(Res.string.map_teaser_caption),
                    color = MarginaliaInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    stringResource(Res.string.map_teaser_count, state.locatedCount.toString()),
                    color = MarginaliaInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
                Button(
                    onClick = onUpgrade,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = OffwhiteWarm),
                ) { Text(stringResource(Res.string.map_teaser_cta)) }
            }
            // Koppar-"PREMIUM"-flagga, samma språk som PremiumTeaserCard.
            Box(
                modifier =
                    Modifier
                        .padding(start = 18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentCopper)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .height(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.premium_lifelist_badge),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.2.em,
                )
            }
        }
    }
}
