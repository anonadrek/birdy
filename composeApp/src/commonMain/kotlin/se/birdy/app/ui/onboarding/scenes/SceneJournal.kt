package se.birdy.app.ui.onboarding.scenes

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s4_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold

@Suppress("UNUSED_PARAMETER")
@Composable
fun SceneJournal(pageOffset: Float, isActive: Boolean) {
    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s4_eyebrow),
        headline = stringResource(Res.string.onboarding_s4_headline),
        sub = stringResource(Res.string.onboarding_s4_sub),
        pageOffset = pageOffset,
    ) {
        // Placeholder — Task 9 fyller in journal-card-mockup
    }
}
