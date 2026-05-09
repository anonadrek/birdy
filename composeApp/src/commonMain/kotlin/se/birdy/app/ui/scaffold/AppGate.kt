package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_fallback_name
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.onboarding.OnboardingScreen
import se.birdy.app.ui.onboarding.OnboardingUiState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.paperBackground

@Composable
fun AppGate(graph: AppGraph) {
    val hasSeen by graph.userPreferences.hasSeenOnboarding.collectAsState(initial = null)

    when (hasSeen) {
        null -> SplashLoading()
        true -> AppScaffold(graph)
        false -> {
            val fallback = stringResource(Res.string.onboarding_p3_fallback_name)
            val vm = remember(graph) { graph.onboardingViewModel(fallback) }
            val state by vm.state.collectAsState()
            when (val s = state) {
                is OnboardingUiState.Visible ->
                    OnboardingScreen(
                        state = s,
                        onPageChange = vm::setPageIndex,
                        onNameChange = vm::onNameChange,
                        onComplete = vm::complete,
                    )
                OnboardingUiState.Done -> AppScaffold(graph)
                OnboardingUiState.Loading -> SplashLoading()
            }
        }
    }
}

@Composable
private fun SplashLoading() {
    Box(modifier = Modifier.fillMaxSize().paperBackground(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentCopper)
    }
}
