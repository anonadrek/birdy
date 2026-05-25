package se.birdy.app.ui.onboarding.scenes

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold

@Composable
fun SceneHero(pageOffset: Float) {
    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s1_eyebrow),
        headline = stringResource(Res.string.onboarding_s1_headline),
        sub = stringResource(Res.string.onboarding_s1_sub),
        pageOffset = pageOffset,
    ) {
        // Placeholder — Task 6 fyller in wordmark + fade-up
    }
}
