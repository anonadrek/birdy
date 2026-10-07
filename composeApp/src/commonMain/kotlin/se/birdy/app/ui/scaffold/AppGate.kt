package se.birdy.app.ui.scaffold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    // Review fix (Release 1.3.0 Plan 3 Task 2c, 2026-10-05): ClassifierBootstrap.retry() sets
    // the state back to Initializing before rebuilding. Gating on EVERY Initializing (as before)
    // replaced the whole AppScaffold/NavHost subtree with this full-screen loader each time a
    // retry fired from Scan or PhotoAnalyze: the bottom nav bar and the rest of AppScaffold's own
    // state disappeared while the model rebuilt, and its start-up effect reran underneath — a
    // jarring, risky teardown of screens that otherwise had nothing to do with the photo model.
    // Only the VERY FIRST Initializing (before AppScaffold has ever been shown) should take over
    // the whole screen; once Ready/Failed has been seen once, a later Initializing must keep
    // rendering the same AppScaffold branch (same navController) so nothing underneath it is torn
    // down. Scan/PhotoAnalyze show their own in-place loader for that later Initializing instead
    // (see ScanScreenHost/PhotoAnalyzeHost); see PhotoModelFailedRoutingTest for the proof (the
    // bottom nav bar, only ever rendered inside AppScaffold, used to disappear during a retry).
    var hasBuiltOnce by remember(graph) { mutableStateOf(false) }
    if (bootstrapState !is ClassifierBootstrapState.Initializing) hasBuiltOnce = true

    if (bootstrapState is ClassifierBootstrapState.Initializing && !hasBuiltOnce) {
        JournalLoading(label = stringResource(Res.string.bootstrap_loading))
        return
    }

    // 2026-10-05 (Albin): a failed photo model no longer blocks the whole app. Scan and
    // photo-ID are the only screens that need AppGraph.classifier — they show their own
    // error state with retry (PhotoModelUnavailableView via ScanScreenHost/PhotoAnalyzeHost).
    // Everything else (Listen/audio-ID, diary, encyclopedia, map, Premium, settings) proceeds
    // exactly as it would once the model is Ready.
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
