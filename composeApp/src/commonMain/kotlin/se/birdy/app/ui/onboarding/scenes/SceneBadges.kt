package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s5_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s5_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s5_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.components.StampTrack
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold

@Composable
fun SceneBadges(
    pageOffset: Float,
    isActive: Boolean,
) {
    val flipDegrees = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            flipDegrees.snapTo(0f)
            kotlinx.coroutines.delay(300) // suspense
            flipDegrees.animateTo(180f, tween(500))
        } else {
            flipDegrees.snapTo(0f)
        }
    }

    val showFront = flipDegrees.value < 90f

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s5_eyebrow),
        headline = stringResource(Res.string.onboarding_s5_headline),
        sub = stringResource(Res.string.onboarding_s5_sub),
        pageOffset = pageOffset,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier.graphicsLayer { rotationY = flipDegrees.value },
                contentAlignment = Alignment.Center,
            ) {
                if (showFront) {
                    StampSeal(state = StampSealState.Locked(name = null))
                } else {
                    Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                        StampSeal(
                            state = StampSealState.Unlocked(number = 7, glyph = "❦", name = null),
                        )
                    }
                }
            }
            StampTrack(
                filled = 5,
                total = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
