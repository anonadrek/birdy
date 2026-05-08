package se.birdy.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mossgrön gradient-zon med rundade botten-hörn.
 *
 * Används som hero på alla redesignade skärmar (Listen / Archive / Lifelist /
 * Badges / Match / Settings). Innehåll (breadcrumb, headline, sub, optional
 * stats) sätts i `content`-slot:en. Padding inuti default = 24dp horisontellt,
 * 28dp vertikalt — anpassa per skärm via `contentPadding`.
 *
 * Bottom-corner-radius 24dp släpper hero från innehållet under utan att vara
 * en kant-till-kant-banner.
 */
@Composable
fun HeroZone(
    modifier: Modifier = Modifier,
    bottomCornerRadius: Dp = 24.dp,
    contentPaddingHorizontal: Dp = 24.dp,
    contentPaddingTop: Dp = 28.dp,
    contentPaddingBottom: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    val gradient =
        remember {
            Brush.verticalGradient(
                colors = listOf(HeroMossLight, HeroMossMid, HeroMossDeep),
            )
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = bottomCornerRadius, bottomEnd = bottomCornerRadius))
                .background(gradient)
                .padding(
                    start = contentPaddingHorizontal,
                    end = contentPaddingHorizontal,
                    top = contentPaddingTop,
                    bottom = contentPaddingBottom,
                ),
    ) {
        content()
    }
}
