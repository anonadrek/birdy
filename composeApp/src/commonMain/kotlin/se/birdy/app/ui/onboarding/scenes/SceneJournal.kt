package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_marginalia
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.rememberCaveat

@Composable
fun SceneJournal(
    pageOffset: Float,
    isActive: Boolean,
) {
    val pageAlpha = remember { Animatable(0f) }
    val pageOffsetY = remember { Animatable(32f) }
    val existingStamp1Alpha = remember { Animatable(0f) }
    val existingStamp2Alpha = remember { Animatable(0f) }
    val newStampScale = remember { Animatable(0f) }
    val marginaliaAlpha = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            pageAlpha.snapTo(0f)
            pageOffsetY.snapTo(32f)
            existingStamp1Alpha.snapTo(0f)
            existingStamp2Alpha.snapTo(0f)
            newStampScale.snapTo(0f)
            marginaliaAlpha.snapTo(0f)
            pageAlpha.animateTo(1f, tween(500))
            pageOffsetY.animateTo(0f, tween(500))
            existingStamp1Alpha.animateTo(0.6f, tween(300))
            existingStamp2Alpha.animateTo(0.6f, tween(300))
            newStampScale.animateTo(1.1f, tween(300))
            newStampScale.animateTo(1.0f, tween(200))
            marginaliaAlpha.animateTo(1f, tween(700))
        } else {
            listOf(pageAlpha, existingStamp1Alpha, existingStamp2Alpha, newStampScale, marginaliaAlpha)
                .forEach { it.snapTo(0f) }
            pageOffsetY.snapTo(32f)
        }
    }

    val caveat = rememberCaveat()

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s4_eyebrow),
        headline = stringResource(Res.string.onboarding_s4_headline),
        sub = stringResource(Res.string.onboarding_s4_sub),
        pageOffset = pageOffset,
    ) {
        // Journal page mockup
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 32.dp)
                    .offset(y = pageOffsetY.value.dp)
                    .alpha(pageAlpha.value)
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PaperTop)
                    .border(1.dp, MarginaliaInk.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(20.dp),
        ) {
            // Pre-existing stamps (top-left + bottom-right)
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .alpha(existingStamp1Alpha.value)
                        .rotate(-6f),
            ) {
                StampSeal(
                    state = StampSealState.Unlocked(number = 12, glyph = null, name = null),
                    size = 56.dp,
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .alpha(existingStamp2Alpha.value)
                        .rotate(4f),
            ) {
                StampSeal(
                    state = StampSealState.Unlocked(number = 27, glyph = null, name = null),
                    size = 56.dp,
                )
            }
            // Slam stamp in middle
            Box(
                modifier = Modifier.align(Alignment.Center).scale(newStampScale.value),
            ) {
                StampSeal(
                    state = StampSealState.Unlocked(number = 28, glyph = null, name = null),
                    size = 88.dp,
                )
            }
            // Marginalia date
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .alpha(marginaliaAlpha.value),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(2.dp).height(20.dp).background(AccentCopper))
                Text(
                    text = "  ${stringResource(Res.string.onboarding_s4_marginalia)}",
                    color = MarginaliaInk,
                    fontFamily = caveat,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
