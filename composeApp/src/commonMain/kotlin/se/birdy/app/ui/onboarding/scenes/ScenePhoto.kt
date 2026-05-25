package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_match_pill
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_species_demo
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_sub
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.PlateFrame
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.util.speciesImageUri

@Composable
fun ScenePhoto(pageOffset: Float, isActive: Boolean) {
    val photoAlpha = remember { Animatable(0f) }
    val stampScale = remember { Animatable(0f) }
    val stampRotation = remember { Animatable(-8f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            photoAlpha.snapTo(0f)
            stampScale.snapTo(0f)
            stampRotation.snapTo(-8f)
            // 400ms photo fade-in
            photoAlpha.animateTo(1f, animationSpec = tween(durationMillis = 400))
            // 500ms stamp slam (scale 0 → 1.1 → 1.0 + rotation -8° → 0°)
            stampScale.animateTo(1.1f, animationSpec = tween(durationMillis = 300))
            stampScale.animateTo(1.0f, animationSpec = tween(durationMillis = 200))
            stampRotation.animateTo(0f, animationSpec = tween(durationMillis = 500))
        } else {
            photoAlpha.snapTo(0f)
            stampScale.snapTo(0f)
            stampRotation.snapTo(-8f)
        }
    }

    val speciesName = stringResource(Res.string.onboarding_s2_species_demo)
    val matchPill = stringResource(Res.string.onboarding_s2_match_pill)

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s2_eyebrow),
        headline = stringResource(Res.string.onboarding_s2_headline),
        sub = stringResource(Res.string.onboarding_s2_sub),
        pageOffset = pageOffset,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Plate-foto
            Box(modifier = Modifier.alpha(photoAlpha.value)) {
                PlateFrame(
                    plateLabel = "I",
                    captionLine = "$speciesName, in nature",
                    image = {
                        AsyncImage(
                            model = speciesImageUri("Q25485/hero.webp"),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    },
                )
            }
            // Camera-frame brackets overlay
            Canvas(modifier = Modifier.fillMaxSize().padding(28.dp)) {
                val s = 18.dp.toPx()
                val sw = 2.5.dp.toPx()
                val w = size.width
                val h = size.height
                val c = AccentCopper
                // 4 corner brackets
                drawLine(c, Offset(0f, 0f), Offset(s, 0f), sw)
                drawLine(c, Offset(0f, 0f), Offset(0f, s), sw)
                drawLine(c, Offset(w, 0f), Offset(w - s, 0f), sw)
                drawLine(c, Offset(w, 0f), Offset(w, s), sw)
                drawLine(c, Offset(0f, h), Offset(s, h), sw)
                drawLine(c, Offset(0f, h), Offset(0f, h - s), sw)
                drawLine(c, Offset(w, h), Offset(w - s, h), sw)
                drawLine(c, Offset(w, h), Offset(w, h - s), sw)
                // Crosshair circle in middle
                drawCircle(
                    color = c,
                    radius = 12.dp.toPx(),
                    center = Offset(w / 2f, h / 2f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            // Slam stamp
            Box(
                modifier =
                    Modifier
                        .scale(stampScale.value)
                        .rotate(stampRotation.value),
            ) {
                StampSeal(
                    state = StampSealState.Unlocked(number = 1, glyph = null, name = matchPill),
                )
            }
        }
    }
}
