package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_free_pill
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_species_demo
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.onboarding.components.WaveformBars
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.TextOnCreme

@Composable
fun SceneAudio(
    pageOffset: Float,
    isActive: Boolean,
) {
    val micScale = remember { Animatable(0f) }
    val ringSweep = remember { Animatable(0f) }
    val labelAlpha = remember { Animatable(0f) }
    val pillScale = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            micScale.snapTo(0f)
            ringSweep.snapTo(0f)
            labelAlpha.snapTo(0f)
            pillScale.snapTo(0f)
            micScale.animateTo(1f, tween(300))
            ringSweep.animateTo(360f, tween(3000))
            labelAlpha.animateTo(1f, tween(400))
            pillScale.animateTo(1.05f, tween(220))
            pillScale.animateTo(1.0f, tween(130))
        } else {
            micScale.snapTo(0f)
            ringSweep.snapTo(0f)
            labelAlpha.snapTo(0f)
            pillScale.snapTo(0f)
        }
    }

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s3_eyebrow),
        headline = stringResource(Res.string.onboarding_s3_headline),
        sub = stringResource(Res.string.onboarding_s3_sub),
        pageOffset = pageOffset,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            // Mic + countdown ring
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(96.dp)) {
                    drawArc(
                        color = AccentCopper,
                        startAngle = -90f,
                        sweepAngle = ringSweep.value,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(64.dp)
                            .scale(micScale.value)
                            .clip(CircleShape)
                            .background(AccentCopper.copy(alpha = 0.12f))
                            .border(1.dp, AccentCopper, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = AccentCopper,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            // Waveform
            WaveformBars(active = isActive, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            // Species name
            Text(
                text = stringResource(Res.string.onboarding_s3_species_demo),
                color = TextOnCreme,
                fontSize = 18.sp,
                fontWeight = FontWeight.W500,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.alpha(labelAlpha.value),
            )
            // GRATIS pill
            Box(
                modifier =
                    Modifier
                        .scale(pillScale.value)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentCopper)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_s3_free_pill),
                    color = OffwhiteWarm,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
