package se.birdy.app.ui.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.components.JournalIntro
import kotlin.math.absoluteValue

/**
 * Wrapper för Onboarding v2-scener 1–6. Standardiserar eyebrow + JournalHeadline
 * + sub + visualSlot, och drar fade + parallax baserat på [pageOffset] som
 * kommer från `pagerState.currentPageOffsetFraction` + (currentPage - thisPage).
 */
@Composable
fun IntroSceneScaffold(
    eyebrow: String,
    headline: String,
    sub: String,
    pageOffset: Float,
    modifier: Modifier = Modifier,
    visual: @Composable () -> Unit,
) {
    val abs = pageOffset.absoluteValue.coerceIn(0f, 1f)
    val visibility = 1f - abs
    val parallaxPx = (pageOffset * 80f) // px-skala — Compose layout-modifier converterar med density

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    // Sanera marginalia-markup (*emfas*) + normalisera punkter så TalkBack inte
                    // läser råa asterisker eller dubbelpunkt (headline slutar ofta redan med ".").
                    contentDescription =
                        listOf(eyebrow, headline, sub)
                            .map { it.replace("*", "").trim().trimEnd('.') }
                            .filter { it.isNotBlank() }
                            .joinToString(". ", postfix = ".")
                }.alpha(visibility)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, parallaxPx.toInt())
                    }
                }.padding(horizontal = 0.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JournalIntro(
            label = eyebrow,
            headline = headline,
            sub = sub,
            headlineFontSize = 36.sp,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            visual()
        }
    }
}
