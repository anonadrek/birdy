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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_fallback_name
import birdy_bird_scanner.composeapp.generated.resources.premium_dismiss_toast
import birdy_bird_scanner.composeapp.generated.resources.premium_welcome_toast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        val premiumState = graph.premiumOverride ?: graph.premiumRepository.state.value

        // Day-0: show the premium screen once right after onboarding (non-premium only).
        val postOnboardingShown = graph.userPreferences.postOnboardingPremiumShown.first()
        if (EntryFlowDecider.shouldShowPostOnboardingPremium(
                onboardingComplete = true,
                alreadyShown = postOnboardingShown,
                state = premiumState,
            )
        ) {
            graph.userPreferences.setPostOnboardingPremiumShown(true)
            navController.navigate(AppRoute.Premium)
            return@LaunchedEffect
        }

        // Otherwise: the 7-day cold-start re-engagement modal.
        val firstInstallMs = graph.userPreferences.firstInstallTimestamp.first()
        val lastShownMs = graph.userPreferences.premiumModalLastShownAt.first()
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
    graph.deepLinkFlow?.let { flow ->
        LaunchedEffect(navController) {
            flow.collect { uriString ->
                val parts = uriString.removePrefix("birdy://").split("/", limit = 2)
                val host = parts.getOrNull(0) ?: return@collect
                val pathSegment = parts.getOrNull(1)?.substringBefore("?")?.takeIf { it.isNotBlank() }
                when (host) {
                    "species" -> {
                        val qid = pathSegment ?: return@collect
                        navController.navigate(AppRoute.SpeciesProfile(qid)) {
                            launchSingleTop = true
                        }
                    }
                    "identify" -> {
                        navController.popBackStack(AppRoute.Listen, inclusive = false)
                    }
                    "recap" -> {
                        navController.navigate(AppRoute.WeeklyRecap) { launchSingleTop = true }
                    }
                }
            }
        }
    }
    var showPermissionSheet by remember { mutableStateOf(false) }
    val notifApi = graph.platformNotificationsApi
    val requestPerm = graph.requestPostNotificationsPermission
    if (notifApi != null && requestPerm != null) {
        LaunchedEffect(Unit) {
            if (!notifApi.needsRuntimePermission()) return@LaunchedEffect
            if (graph.userPreferences.pushPermissionAsked.first()) return@LaunchedEffect
            if (notifApi.areNotificationsEnabled()) {
                // System already grants it — record asked = true and bail.
                graph.userPreferences.setPushPermissionAsked(true)
                return@LaunchedEffect
            }
            graph.observationRepository
                .observeAll()
                .first { it.isNotEmpty() }
            showPermissionSheet = true
        }
    }
    if (showPermissionSheet) {
        se.birdy.app.ui.components.PermissionPromptSheet(
            onTurnOn = {
                requestPerm?.invoke()
                showPermissionSheet = false
            },
            onDismiss = {
                scope.launch {
                    graph.userPreferences.setPushPermissionAsked(true)
                }
                showPermissionSheet = false
            },
        )
    }
    val bottomBarEntry by navController.currentBackStackEntryAsState()
    // Onboarding-replayen är en uppslukande helskärms-story — dölj bottenflikarna där
    // (övriga detaljskärmar behåller dem, som tidigare).
    val hideBottomBar = bottomBarEntry?.destination?.hasRoute(AppRoute.OnboardingReplay::class) == true
    Scaffold(
        bottomBar = { if (!hideBottomBar) BottomNavBar(navController) },
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
                    onNavigateToAudioScan = {
                        navController.navigate(AppRoute.AudioScan) { launchSingleTop = true }
                    },
                    onSpeciesProfileClick = { speciesId ->
                        navController.navigate(AppRoute.SpeciesProfile(speciesId)) {
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
                    onBack = {
                        navController.popBackStack(AppRoute.Listen, inclusive = false)
                    },
                )
            }
            composable<AppRoute.PhotoAnalyze> {
                se.birdy.app.ui.photoanalyze.PhotoAnalyzeHost(
                    graph = graph,
                    onLoaded = { sourceJson, capturedAtMs ->
                        navController.navigate(AppRoute.MatchResult(sourceJson, capturedAtMs)) {
                            popUpTo(AppRoute.Scan) { inclusive = false }
                        }
                    },
                    onBack = {
                        navController.popBackStack(AppRoute.Listen, inclusive = false)
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
                        locale = graph.defaultLocale,
                        onSpeciesClick = { id -> navController.navigate(AppRoute.SpeciesProfile(id.raw)) },
                        onPremiumClick = { navController.navigate(AppRoute.Premium) },
                        onJournalExport = graph.journalExport,
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
                        locale = graph.defaultLocale,
                        onBack = { navController.popBackStack() },
                        onPremiumClick = { navController.navigate(AppRoute.Premium) },
                        showPremiumTeaser = showPremiumTeaser,
                    )
                }
            }
            composable<AppRoute.Lifelist> {
                val livePreviewState =
                    if (effectivePremiumActive) {
                        val seasonStatsVm = remember(graph) { graph.seasonStatsViewModel() }
                        LaunchedEffect(seasonStatsVm) { seasonStatsVm.onEnter() }
                        val state by seasonStatsVm.state.collectAsState()
                        state as? se.birdy.app.ui.stats.SeasonStatsUiState.Loaded
                    } else {
                        null
                    }
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
                    livePreviewState = livePreviewState,
                    onSeasonStatsClick = { navController.navigate(AppRoute.SeasonStats) },
                    onRecapClick = { navController.navigate(AppRoute.WeeklyRecap) { launchSingleTop = true } },
                )
            }
            composable<AppRoute.Map> {
                val mapVm = remember(graph) { graph.mapViewModel() }
                if (effectivePremiumActive) {
                    se.birdy.app.ui.map.MapScreen(
                        viewModel = mapVm,
                        onPinClick = { id -> navController.navigate(AppRoute.ObservationDetail(id)) },
                    )
                } else {
                    se.birdy.app.ui.map.MapPremiumTeaser(
                        viewModel = mapVm,
                        onUpgrade = { navController.navigate(AppRoute.Premium) },
                    )
                }
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
                    onOpenTrophyRoom = { navController.navigate(AppRoute.TrophyRoom) { launchSingleTop = true } },
                    showPremiumTeaser = showPremiumTeaser,
                )
            }
            composable<AppRoute.TrophyRoom> {
                TrophyRoomRoute(
                    graph = graph,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<AppRoute.Settings> {
                se.birdy.app.ui.settings.SettingsScreen(
                    viewModel = remember(graph) { graph.settingsViewModel() },
                    onBack = { navController.popBackStack() },
                    onPremiumClick = { navController.navigate(AppRoute.Premium) },
                    onNavigateToAbout = { navController.navigate(AppRoute.About) },
                    onShowIntroAgain = { navController.navigate(AppRoute.OnboardingReplay) },
                    versionName = graph.versionName,
                    onRequestLocationPermission = { graph.requestLocationPermission?.invoke() },
                )
            }
            composable<AppRoute.About> {
                se.birdy.app.ui.settings.AboutScreen(
                    onBack = { navController.popBackStack() },
                    version = graph.versionName,
                )
            }
            composable<AppRoute.OnboardingReplay> {
                val fallback = stringResource(Res.string.onboarding_p3_fallback_name)
                val vm = remember(graph) { graph.onboardingViewModel(fallback, isReplay = true) }
                val state by vm.state.collectAsState()
                when (val s = state) {
                    is se.birdy.app.ui.onboarding.OnboardingUiState.Visible ->
                        se.birdy.app.ui.onboarding.OnboardingScreen(
                            state = s,
                            onPageChange = vm::setPageIndex,
                            onNameChange = vm::onNameChange,
                            onComplete = { navController.popBackStack() },
                            isReplay = true,
                        )
                    se.birdy.app.ui.onboarding.OnboardingUiState.Done -> {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                    se.birdy.app.ui.onboarding.OnboardingUiState.Loading -> Unit
                }
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
                    onNavigateToMatch = { sourceJson, capturedAtMs ->
                        navController.navigate(AppRoute.MatchResult(sourceJson, capturedAtMs)) {
                            popUpTo(AppRoute.Listen) { inclusive = false }
                        }
                    },
                    onBack = {
                        navController.popBackStack(AppRoute.Listen, inclusive = false)
                    },
                )
            }
            composable<AppRoute.SeasonStats> {
                LaunchedEffect(effectivePremiumActive) {
                    if (!effectivePremiumActive) {
                        navController.popBackStack()
                    }
                }
                se.birdy.app.ui.stats.SeasonStatsScreen(
                    viewModel = remember(graph) { graph.seasonStatsViewModel() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<AppRoute.WeeklyRecap> {
                se.birdy.app.ui.recap.RecapScreen(
                    viewModel = remember(graph) { graph.weeklyRecapViewModel() },
                    onOpenCamera = {
                        navController.navigate(AppRoute.Scan) { launchSingleTop = true }
                    },
                    onObservationClick = { id -> navController.navigate(AppRoute.ObservationDetail(id)) },
                )
            }
        }
    }
}
