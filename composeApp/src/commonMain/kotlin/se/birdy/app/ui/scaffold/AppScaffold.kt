package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.datetime.Clock
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.diary.LifelistScreen
import se.birdy.app.ui.diary.ObservationDetailScreen
import se.birdy.app.ui.encyclopedia.ArchiveScreen
import se.birdy.app.ui.listen.ListenLauncherScreen
import se.birdy.app.ui.match.MatchResultScreen
import se.birdy.app.ui.premium.PremiumScreen
import se.birdy.app.ui.profile.SpeciesProfileScreen
import se.birdy.app.ui.scan.ScanScreenHost
import se.birdy.content.SpeciesId

@Composable
fun AppScaffold(graph: AppGraph) {
    val navController = rememberNavController()
    Scaffold(bottomBar = { BottomNavBar(navController) }) { padding ->
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
                    onFrozen = { csv, path, capturedAtMs ->
                        navController.navigate(AppRoute.MatchResult(csv, path, capturedAtMs))
                    },
                )
            }
            composable<AppRoute.PhotoAnalyze> {
                se.birdy.app.ui.photoanalyze.PhotoAnalyzeHost(
                    graph = graph,
                    onLoaded = { csv, path ->
                        val ts = Clock.System.now().toEpochMilliseconds()
                        navController.navigate(AppRoute.MatchResult(csv, path, ts)) {
                            popUpTo(AppRoute.Scan) { inclusive = false }
                        }
                    },
                )
            }
            composable<AppRoute.MatchResult> { entry ->
                val route = entry.toRoute<AppRoute.MatchResult>()
                val vm =
                    remember(graph, route) {
                        graph.matchResultViewModel(route.predictionsCsv, route.frameJpegPath, route.capturedAtMs)
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
                        showDebugMenu = graph.benchmarkScreen != null,
                        onDebugBenchmarkClick = { navController.navigate(AppRoute.DebugBenchmark) },
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
            composable<AppRoute.Badges> { BadgesRoute(graph) }
            composable<AppRoute.Settings> {
                se.birdy.app.ui.settings.SettingsScreen(
                    viewModel = remember(graph) { graph.settingsViewModel() },
                    onBack = { navController.popBackStack() },
                    onPremiumClick = { navController.navigate(AppRoute.Premium) },
                    onRowClick = { /* TODO: wire row-actions in Plan 6 (Rate/Share/Feedback/About/Privacy/Terms) */ },
                )
            }
            composable<AppRoute.Premium> {
                PremiumScreen(
                    viewModel = remember(graph) { graph.premiumViewModel() },
                    onClose = { navController.popBackStack() },
                    onPurchaseComplete = { navController.popBackStack(AppRoute.Premium, inclusive = true) },
                )
            }
            graph.benchmarkScreen?.let { benchmarkContent ->
                composable<AppRoute.DebugBenchmark> { benchmarkContent() }
            }
        }
    }
}
