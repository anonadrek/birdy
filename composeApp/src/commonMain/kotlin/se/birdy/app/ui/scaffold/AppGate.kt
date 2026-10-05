package se.birdy.app.ui.scaffold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.bootstrap_loading
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.onboarding.OnboardingScreen
import se.birdy.app.ui.onboarding.OnboardingUiState
import se.birdy.ml.ClassifierBootstrapState

@Composable
fun AppGate(
    graph: AppGraph,
    // Tests pass their own controller to drive navigation directly (Release 1.3.0 Plan 3 Task
    // 2c, mirrors AppScaffold's own test seam); the app uses the default.
    navController: NavHostController = rememberNavController(),
) {
    val bootstrapState by graph.classifierBootstrap.state.collectAsState()
    val hasSeen by graph.userPreferences.hasSeenOnboarding.collectAsState(initial = null)

    when (bootstrapState) {
        is ClassifierBootstrapState.Initializing -> JournalLoading(label = stringResource(Res.string.bootstrap_loading))
        // 2026-10-05 (Albin): a failed photo model no longer blocks the whole app. Scan and
        // photo-ID are the only screens that need AppGraph.classifier — they show their own
        // error state with retry (PhotoModelUnavailableView via ScanScreenHost/PhotoAnalyzeHost).
        // Everything else (Listen/audio-ID, diary, encyclopedia, map, Premium, settings) proceeds
        // exactly as it would once the model is Ready.
        is ClassifierBootstrapState.Failed, is ClassifierBootstrapState.Ready -> {
            when (hasSeen) {
                null -> JournalLoading()
                true -> AppScaffold(graph, navController)
                false -> {
                    val vm = remember(graph) { graph.onboardingViewModel() }
                    val state by vm.state.collectAsState()
                    when (val s = state) {
                        is OnboardingUiState.Visible ->
                            OnboardingScreen(
                                state = s,
                                onPageChange = vm::setPageIndex,
                                onNameChange = vm::onNameChange,
                                onLanguageSelect = vm::selectLanguage,
                                onComplete = vm::complete,
                            )
                        OnboardingUiState.Done -> AppScaffold(graph, navController)
                        OnboardingUiState.Loading -> JournalLoading()
                    }
                }
            }
        }
    }
}
