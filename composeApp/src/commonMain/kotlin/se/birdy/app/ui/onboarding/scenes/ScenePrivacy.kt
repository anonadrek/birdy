package se.birdy.app.ui.onboarding.scenes

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s6_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s6_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s6_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold

@Suppress("UNUSED_PARAMETER")
@Composable
fun ScenePrivacy(pageOffset: Float, isActive: Boolean) {
    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s6_eyebrow),
        headline = stringResource(Res.string.onboarding_s6_headline),
        sub = stringResource(Res.string.onboarding_s6_sub),
        pageOffset = pageOffset,
    ) {
        // Placeholder — Task 11 fyller in privacy-illustration
    }
}
