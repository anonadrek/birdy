package se.birdy.app.ui.onboarding.scenes

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s3_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold

@Suppress("UNUSED_PARAMETER")
@Composable
fun SceneAudio(pageOffset: Float, isActive: Boolean) {
    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s3_eyebrow),
        headline = stringResource(Res.string.onboarding_s3_headline),
        sub = stringResource(Res.string.onboarding_s3_sub),
        pageOffset = pageOffset,
    ) {
        // Placeholder — Task 8 fyller in waveform-animation
    }
}
