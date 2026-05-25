package se.birdy.app.ui.onboarding.scenes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_headline
import birdy_bird_scanner.composeapp.generated.resources.onboarding_s7_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro

@Suppress("UNUSED_PARAMETER")
@Composable
fun SceneName(
    nameInput: String,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        JournalIntro(
            label = stringResource(Res.string.onboarding_s7_eyebrow),
            headline = stringResource(Res.string.onboarding_s7_headline),
            sub = stringResource(Res.string.onboarding_s7_sub),
        )
        Text("Placeholder för name-input — fylls i Task 12")
    }
}
