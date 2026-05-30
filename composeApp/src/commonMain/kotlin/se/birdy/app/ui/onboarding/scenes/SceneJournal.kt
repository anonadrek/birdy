package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_marginalia
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_species
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_sub
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperBottom
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@OptIn(ExperimentalResourceApi::class)
@Composable
fun SceneJournal(
    pageOffset: Float,
    isActive: Boolean,
) {
    val pageAlpha = remember { Animatable(0f) }
    val pageOffsetY = remember { Animatable(32f) }
    val plateAlpha = remember { Animatable(0f) }
    val newStampScale = remember { Animatable(0f) }
    val marginaliaAlpha = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            pageAlpha.snapTo(0f)
            pageOffsetY.snapTo(32f)
            plateAlpha.snapTo(0f)
            newStampScale.snapTo(0f)
            marginaliaAlpha.snapTo(0f)
            pageAlpha.animateTo(1f, tween(500))
            pageOffsetY.animateTo(0f, tween(500))
            plateAlpha.animateTo(1f, tween(350))
            newStampScale.animateTo(1.12f, tween(280))
            newStampScale.animateTo(1f, tween(180))
            marginaliaAlpha.animateTo(1f, tween(700))
        } else {
            listOf(pageAlpha, plateAlpha, newStampScale, marginaliaAlpha).forEach { it.snapTo(0f) }
            pageOffsetY.snapTo(32f)
        }
    }

    val caveat = rememberCaveat()
    val serif = rememberDmSerifDisplay()

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s4_eyebrow),
        headline = stringResource(Res.string.onboarding_s4_headline),
        sub = stringResource(Res.string.onboarding_s4_sub),
        pageOffset = pageOffset,
    ) {
        // Journal page mockup — a real field-journal entry: a framed find + species
        // name + scientific name + notes, on warm aged paper, with the new seal
        // slammed into the top-right corner.
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 32.dp)
                    .offset(y = pageOffsetY.value.dp)
                    .alpha(pageAlpha.value)
                    .fillMaxWidth()
                    .height(280.dp)
                    .shadow(elevation = 5.dp, shape = RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(PaperTop, SandCreme)))
                    .border(1.dp, MarginaliaInk.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(20.dp),
        ) {
            // Entry: species name + framed find + scientific name + note lines.
            // end-padding keeps the entry clear of the corner seal.
            Column(modifier = Modifier.fillMaxWidth().padding(end = 60.dp)) {
                Text(
                    text = stringResource(Res.string.onboarding_s4_species),
                    color = MarginaliaInk,
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 22.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    // Framed find — naturalist plate
                    Box(
                        modifier =
                            Modifier
                                .alpha(plateAlpha.value)
                                .size(width = 96.dp, height = 104.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PaperBottom.copy(alpha = 0.6f))
                                .border(
                                    1.dp,
                                    AccentCopper.copy(alpha = 0.35f),
                                    RoundedCornerShape(8.dp),
                                ).padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = Res.getUri("files/branding/hero_bird.png"),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.alpha(marginaliaAlpha.value).padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Text(
                            text = "Apus apus",
                            color = MarginaliaInk.copy(alpha = 0.8f),
                            fontFamily = caveat,
                            fontStyle = FontStyle.Italic,
                            fontSize = 16.sp,
                        )
                        // Faint handwritten-note rule lines
                        Box(
                            Modifier
                                .height(2.dp)
                                .fillMaxWidth()
                                .background(MarginaliaInk.copy(alpha = 0.16f)),
                        )
                        Box(
                            Modifier
                                .height(2.dp)
                                .fillMaxWidth(0.82f)
                                .background(MarginaliaInk.copy(alpha = 0.16f)),
                        )
                        Box(
                            Modifier
                                .height(2.dp)
                                .fillMaxWidth(0.55f)
                                .background(MarginaliaInk.copy(alpha = 0.16f)),
                        )
                    }
                }
            }

            // New seal slammed into the top-right corner
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .scale(newStampScale.value)
                        .rotate(6f),
            ) {
                StampSeal(
                    state = StampSealState.Unlocked(number = 28, glyph = null, name = null),
                    size = 72.dp,
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
