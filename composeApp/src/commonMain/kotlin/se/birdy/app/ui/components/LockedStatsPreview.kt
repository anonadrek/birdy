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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.rememberCaveat

/**
 * Blurred fake-staplar med 🔒-overlay. Premium-teaser för Lifelist-skärmen.
 * Stapeldatan är hardcoded i v1.
 */
@Composable
fun LockedStatsPreview(
    title: String,
    overlayCta: String,
    overlayBadge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barHeights = listOf(0.30f, 0.55f, 0.80f, 0.65f, 0.45f, 0.75f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = overlayCta
                    role = Role.Button
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(SandCreme)
                    .padding(14.dp)
                    .blur(3.5.dp)
                    .graphicsLayer(alpha = 0.55f),
        ) {
            Text(title, fontSize = 11.sp, color = MarginaliaInk)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(70.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                barHeights.forEach { h ->
                    Box(
                        modifier =
                            Modifier
                                .width(20.dp)
                                .height((70 * h).dp)
                                .background(AccentCopper, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                    )
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to PaperTop.copy(alpha = 0f), 1f to PaperTop.copy(alpha = 0.85f))),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = AccentCopper, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                text = overlayCta,
                fontFamily = rememberCaveat(),
                fontSize = 18.sp,
                color = AccentCopper,
                fontWeight = FontWeight.W600,
            )
            Text(
                text = overlayBadge,
                fontSize = 9.sp,
                color = AccentCopper,
                fontWeight = FontWeight.W700,
                letterSpacing = 0.2.em,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
