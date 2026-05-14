package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.bootstrap_failed_body
import birdy_bird_scanner.composeapp.generated.resources.bootstrap_failed_retry
import birdy_bird_scanner.composeapp.generated.resources.bootstrap_failed_title
import birdy_bird_scanner.composeapp.generated.resources.bootstrap_loading
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_fallback_name
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.components.JournalDialog
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.onboarding.OnboardingScreen
import se.birdy.app.ui.onboarding.OnboardingUiState
import se.birdy.app.ui.theme.paperBackground
import se.birdy.ml.ClassifierBootstrapState

@Composable
fun AppGate(graph: AppGraph) {
    val bootstrapState by graph.classifierBootstrap.state.collectAsState()
    val hasSeen by graph.userPreferences.hasSeenOnboarding.collectAsState(initial = null)

    when (bootstrapState) {
        is ClassifierBootstrapState.Initializing -> JournalLoading(label = stringResource(Res.string.bootstrap_loading))
        is ClassifierBootstrapState.Failed ->
            BootstrapFailedView(
                onRetry = { graph.classifierBootstrap.retry() },
            )
        is ClassifierBootstrapState.Ready -> {
            when (hasSeen) {
                null -> JournalLoading()
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
                        OnboardingUiState.Loading -> JournalLoading()
                    }
                }
            }
        }
    }
}

@Composable
private fun BootstrapFailedView(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().paperBackground())
    JournalDialog(
        title = stringResource(Res.string.bootstrap_failed_title),
        body = stringResource(Res.string.bootstrap_failed_body),
        confirmLabel = stringResource(Res.string.bootstrap_failed_retry),
        onConfirm = onRetry,
        dismissLabel = null,
    )
}
