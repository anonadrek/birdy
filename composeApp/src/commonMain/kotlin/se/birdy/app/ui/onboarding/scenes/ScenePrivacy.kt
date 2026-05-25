package se.birdy.app.ui.onboarding.scenes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s6_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s6_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s6_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.onboarding.components.OfflineShield
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk

@Composable
fun ScenePrivacy(pageOffset: Float, isActive: Boolean) {
    val deviceAlpha = remember { Animatable(0f) }
    val lockScale = remember { Animatable(0f) }
    val shieldProgress = remember { Animatable(0f) }
    val wifiAlpha = remember { Animatable(0f) }
    val wifiStrokeProgress = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            listOf(deviceAlpha, lockScale, shieldProgress, wifiAlpha, wifiStrokeProgress).forEach { it.snapTo(0f) }
            deviceAlpha.animateTo(1f, tween(300))
            lockScale.animateTo(1.2f, tween(280))
            lockScale.animateTo(1.0f, tween(120))
            shieldProgress.animateTo(1f, tween(700))
            wifiAlpha.animateTo(0.6f, tween(200))
            wifiStrokeProgress.animateTo(1f, tween(200))
        } else {
            listOf(deviceAlpha, lockScale, shieldProgress, wifiAlpha, wifiStrokeProgress).forEach { it.snapTo(0f) }
        }
    }

    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s6_eyebrow),
        headline = stringResource(Res.string.onboarding_s6_headline),
        sub = stringResource(Res.string.onboarding_s6_sub),
        pageOffset = pageOffset,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            // Device + lock + shield outline
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                OfflineShield(progress = shieldProgress.value, modifier = Modifier.size(160.dp))
                Box(modifier = Modifier.alpha(deviceAlpha.value)) {
                    Icon(
                        imageVector = Icons.Outlined.Smartphone,
                        contentDescription = null,
                        tint = MarginaliaInk,
                        modifier = Modifier.size(72.dp),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .scale(lockScale.value)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(AccentCopper),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // No-wifi
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.alpha(wifiAlpha.value)) {
                    Icon(
                        imageVector = Icons.Filled.WifiOff,
                        contentDescription = null,
                        tint = MarginaliaInk.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp),
                    )
                }
                // diagonal strike via rotated thin Box
                Box(
                    modifier =
                        Modifier
                            .size(width = (48f * wifiStrokeProgress.value).dp, height = 2.dp)
                            .rotate(35f)
                            .background(AccentCopper),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
