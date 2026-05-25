package se.birdy.app.ui.onboarding.scenes

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s2_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold

@Suppress("UNUSED_PARAMETER")
@Composable
fun ScenePhoto(pageOffset: Float, isActive: Boolean) {
    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s2_eyebrow),
        headline = stringResource(Res.string.onboarding_s2_headline),
        sub = stringResource(Res.string.onboarding_s2_sub),
        pageOffset = pageOffset,
    ) {
        // Placeholder — Task 7 fyller in kamera-mockup
    }
}
