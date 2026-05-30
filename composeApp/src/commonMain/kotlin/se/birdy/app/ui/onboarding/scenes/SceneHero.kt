package se.birdy.app.ui.onboarding.scenes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s1_sub
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold

@OptIn(ExperimentalResourceApi::class)
@Composable
fun SceneHero(pageOffset: Float) {
    IntroSceneScaffold(
        eyebrow = stringResource(Res.string.onboarding_s1_eyebrow),
        headline = stringResource(Res.string.onboarding_s1_headline),
        sub = stringResource(Res.string.onboarding_s1_sub),
        pageOffset = pageOffset,
    ) {
        AsyncImage(
            model = Res.getUri("files/branding/hero_bird.png"),
            contentDescription = "Birdy",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 96.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
