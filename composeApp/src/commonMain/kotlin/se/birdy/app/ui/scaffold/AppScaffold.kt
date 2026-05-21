package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.premium_dismiss_toast
import birdy_bird_scanner.composeapp.generated.resources.premium_welcome_toast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.di.AppGraph
import se.birdy.app.premium.EntryFlowDecider
import se.birdy.app.ui.audio.AudioScanScreenHost
import se.birdy.app.ui.components.CaveatToast
import se.birdy.app.ui.diary.LifelistScreen
import se.birdy.app.ui.diary.ObservationDetailScreen
import se.birdy.app.ui.encyclopedia.ArchiveScreen
import se.birdy.app.ui.listen.ListenLauncherScreen
import se.birdy.app.ui.match.MatchResultScreen
import se.birdy.app.ui.premium.PremiumScreen
import se.birdy.app.ui.profile.SpeciesProfileScreen
import se.birdy.app.ui.scan.ScanScreenHost
import se.birdy.content.SpeciesId
import se.birdy.domain.premium.PremiumState

@Composable
fun AppScaffold(graph: AppGraph) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dismissToast = stringResource(Res.string.premium_dismiss_toast)
    val welcomeToast = stringResource(Res.string.premium_welcome_toast)
    val backendState by graph.premiumRepository.state.collectAsState()
    val effectivePremiumActive by remember(graph) {
        derivedStateOf {
            val effective = graph.premiumOverride ?: backendState
            effective is PremiumState.Active
        }
    }
    val showPremiumTeaser = !effectivePremiumActive
    LaunchedEffect(Unit) {
        val now = graph.clock.now()
        val firstInstallMs = graph.userPreferences.firstInstallTimestamp.first()
        val lastShownMs = graph.userPreferences.premiumModalLastShownAt.first()
        val premiumState = graph.premiumOverride ?: graph.premiumRepository.state.value
        val shouldShow =
            EntryFlowDecider.shouldShowPremiumModal(
                now = now,
                firstInstallAt = firstInstallMs?.let { Instant.fromEpochMilliseconds(it) },
                lastShownAt = lastShownMs?.let { Instant.fromEpochMilliseconds(it) },
                state = premiumState,
                onboardingComplete = true,
            )
        if (shouldShow) {
            graph.userPreferences.setPremiumModalLastShownAt(now.toEpochMilliseconds())
            navController.navigate(AppRoute.Premium)
        }
    }
    Scaffold(
        bottomBar = { BottomNavBar(navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> CaveatToast(data) } },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Listen,
            modifier = Modifier.padding(padding),
        ) {
            composable<AppRoute.Listen> {
                ListenLauncherScreen(
                    viewModel = remember(graph) { graph.listenLauncherViewModel() },
                    onCameraClick = {
                        navController.navigate(AppRoute.Scan) {
                            launchSingleTop = true
                        }
                    },
                    onPhotoClick = {
                        navController.navigate(AppRoute.PhotoAnalyze) {
                            launchSingleTop = true
                        }
                    },
                    onSettingsClick = {
                        navController.navigate(AppRoute.Settings) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<AppRoute.Scan> {
                ScanScreenHost(
                    graph = graph,
                    onPhotoAnalyzeClick = { navController.navigate(AppRoute.PhotoAnalyze) },
                    onFrozen = { sourceJson, capturedAtMs ->
                        navController.navigate(AppRoute.MatchResult(sourceJson, capturedAtMs))
                    },
                )
            }
            composable<AppRoute.PhotoAnalyze> {
                se.birdy.app.ui.photoanalyze.PhotoAnalyzeHost(
                    graph = graph,
                    onLoaded = { sourceJson ->
                        val ts = Clock.System.now().toEpochMilliseconds()
                        navController.navigate(AppRoute.MatchResult(sourceJson, ts)) {
                            popUpTo(AppRoute.Scan) { inclusive = false }
                        }
                    },
                )
            }
            composable<AppRoute.MatchResult> { entry ->
                val route = entry.toRoute<AppRoute.MatchResult>()
                val vm =
                    remember(graph, route) {
                        graph.matchResultViewModel(route.sourceJson, route.capturedAtMs)
                    }
                MatchResultScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    locale = graph.defaultLocale,
                    zone = graph.timeZone,
                )
            }
            navigation<AppRoute.Archive>(startDestination = AppRoute.ArchiveList) {
                composable<AppRoute.ArchiveList> {
                    ArchiveScreen(
                        viewModel = remember(graph) { graph.archiveViewModel() },
                        onSpeciesClick = { id -> navController.navigate(AppRoute.SpeciesProfile(id.raw)) },
                        onPremiumClick = { navController.navigate(AppRoute.Premium) },
                        showPremiumTeaser = showPremiumTeaser,
                        showDebugMenu = graph.benchmarkScreen != null || graph.diagnosticsScreen != null,
                        onDebugBenchmarkClick = { navController.navigate(AppRoute.DebugBenchmark) },
                        showDebugDiagnostics = graph.diagnosticsScreen != null,
                        onDebugDiagnosticsClick = { navController.navigate(AppRoute.DebugDiagnostics) },
                        onSettingsClick = { navController.navigate(AppRoute.Settings) },
                    )
                }
                composable<AppRoute.SpeciesProfile> { entry ->
                    val route = entry.toRoute<AppRoute.SpeciesProfile>()
                    SpeciesProfileScreen(
                        viewModel =
                            remember(graph, route.speciesId) {
                                graph.speciesProfileViewModel(SpeciesId(route.speciesId))
                            },
                        onBack = { navController.popBackStack() },
                        onPremiumClick = { navController.navigate(AppRoute.Premium) },
                        showPremiumTeaser = showPremiumTeaser,
                    )
                }
            }
            composable<AppRoute.Lifelist> {
                LifelistScreen(
                    viewModel = remember(graph) { graph.lifelistViewModel() },
                    onObservationClick = { id -> navController.navigate(AppRoute.ObservationDetail(id)) },
                    onScanCtaClick = {
                        navController.navigate(AppRoute.Listen) {
                            popUpTo(AppRoute.Listen) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onPremiumClick = { navController.navigate(AppRoute.Premium) },
                    showPremiumTeaser = showPremiumTeaser,
                )
            }
            composable<AppRoute.ObservationDetail> { entry ->
                val route = entry.toRoute<AppRoute.ObservationDetail>()
                ObservationDetailScreen(
                    viewModel = remember(graph, route.id) { graph.observationDetailViewModel(route.id) },
                    onBack = { navController.popBackStack() },
                    onSpeciesClick = { id -> navController.navigate(AppRoute.SpeciesProfile(id)) },
                )
            }
            composable<AppRoute.Badges> {
                BadgesRoute(
                    graph = graph,
                    onSettingsClick = { navController.navigate(AppRoute.Settings) { launchSingleTop = true } },
                    onPremiumClick = { navController.navigate(AppRoute.Premium) },
                    showPremiumTeaser = showPremiumTeaser,
                )
            }
            composable<AppRoute.Settings> {
                se.birdy.app.ui.settings.SettingsScreen(
                    viewModel = remember(graph) { graph.settingsViewModel() },
                    onBack = { navController.popBackStack() },
                    onPremiumClick = { navController.navigate(AppRoute.Premium) },
                    onNavigateToAbout = { navController.navigate(AppRoute.About) },
                    versionName = graph.versionName,
                )
            }
            composable<AppRoute.About> {
                se.birdy.app.ui.settings.AboutScreen(
                    onBack = { navController.popBackStack() },
                    version = graph.versionName,
                )
            }
            composable<AppRoute.Premium> {
                PremiumScreen(
                    viewModel = remember(graph) { graph.premiumViewModel() },
                    onClose = {
                        navController.popBackStack()
                        scope.launch { snackbarHostState.showSnackbar(dismissToast) }
                    },
                    onPurchaseComplete = {
                        navController.popBackStack(AppRoute.Premium, inclusive = true)
                        scope.launch { snackbarHostState.showSnackbar(welcomeToast) }
                    },
                )
            }
            graph.benchmarkScreen?.let { benchmarkContent ->
                composable<AppRoute.DebugBenchmark> { benchmarkContent() }
            }
            graph.diagnosticsScreen?.let { diagnosticsContent ->
                composable<AppRoute.DebugDiagnostics> { diagnosticsContent() }
            }
            composable<AppRoute.AudioScan> {
                AudioScanScreenHost(
                    graph = graph,
                    onNavigateToMatch = { sourceJson ->
                        val ts = Clock.System.now().toEpochMilliseconds()
                        navController.navigate(AppRoute.MatchResult(sourceJson, ts)) {
                            popUpTo(AppRoute.Listen) { inclusive = false }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
