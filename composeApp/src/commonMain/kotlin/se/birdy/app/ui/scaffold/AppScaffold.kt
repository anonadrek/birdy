package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.premium_dismiss_toast
import birdy_bird_scanner.composeapp.generated.resources.premium_welcome_toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.di.AppGraph
import se.birdy.app.premium.EntryFlowDecider
import se.birdy.app.premium.PremiumOverrideResolver
import se.birdy.app.premium.awaitBillingAnswer
import se.birdy.app.ui.audio.AudioScanScreenHost
import se.birdy.app.ui.components.BackTopBar
import se.birdy.app.ui.components.CaveatToast
import se.birdy.app.ui.components.LocalStatusBarBackdrop
import se.birdy.app.ui.components.PlatformNavigationBarIcons
import se.birdy.app.ui.components.PlatformStatusBarIcons
import se.birdy.app.ui.components.StatusBarBackdrop
import se.birdy.app.ui.diary.LifelistScreen
import se.birdy.app.ui.diary.ObservationDetailScreen
import se.birdy.app.ui.encyclopedia.ArchiveScreen
import se.birdy.app.ui.listen.ListenLauncherScreen
import se.birdy.app.ui.match.MatchResultScreen
import se.birdy.app.ui.premium.PremiumScreen
import se.birdy.app.ui.premium.PremiumThankYouScreen
import se.birdy.app.ui.profile.SpeciesProfileScreen
import se.birdy.app.ui.scan.ScanScreenHost
import se.birdy.content.SpeciesId
import se.birdy.domain.premium.PremiumState

// Pre-existing debt, unchanged by Plan 3 Task 1's navController seam: detekt's baseline IDs are
// keyed by the literal signature text, so adding the test-only navController parameter re-keys
// both findings away from the committed detekt-baseline.xml entries without changing the
// function's actual length/complexity. Suppressed here instead of touching the baseline (repo
// convention: extend a baseline only for genuinely new debt).
@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppScaffold(
    graph: AppGraph,
    // Tests pass their own controller to open a route directly (Plan 3 Task 1); the app uses the default.
    navController: NavHostController = rememberNavController(),
) {
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
    // See PremiumOverrideResolver.isEarlyMember's KDoc: the DEBUG skip-override toggle must
    // win, so this gates on the override actually being present, not just isGrandfathered.
    val isEarlyMember = PremiumOverrideResolver.isEarlyMember(graph.isGrandfathered, graph.premiumOverride)
    LaunchedEffect(Unit) {
        val now = graph.clock.now()

        // Never show an automatic paywall on a guess — a paying user looks Free until Play answers.
        if (graph.premiumOverride == null && !awaitBillingAnswer(graph.premiumQueried, timeoutMs = 5_000)) {
            return@LaunchedEffect
        }

        // The wait above can take up to 5 s — if the user has since navigated away from the
        // start destination (opened Scan/Camera, followed a deep link, ...) a paywall popping
        // up now would cover whatever they're doing. Bail without marking either "shown" flag
        // so the paywall is reconsidered fresh next launch. currentBackStackEntryFlow replays
        // the current entry and first() suspends until the NavHost has set its graph, so this
        // can't run against a null destination when the wait above didn't run (override users
        // skip it) — currentDestination could be null in that case.
        if (!navController.currentBackStackEntryFlow
                .first()
                .destination
                .hasRoute(AppRoute.Listen::class)
        ) {
            return@LaunchedEffect
        }

        // 1.3.0: early users get a one-time thank-you instead of any paywall (spec §5.2).
        if (isEarlyMember) {
            val thanksShown = graph.userPreferences.grandfatherThanksShown.first()
            // The read above suspends — re-check synchronously (nothing suspends between here
            // and the navigate below) that a deep link handled during that gap (e.g. a
            // notification tap) hasn't already navigated away from Listen. If it has, bail
            // WITHOUT saving "shown" so the thank-you is reconsidered at a later start instead
            // of covering whatever the deep link opened.
            if (navController.currentDestination?.hasRoute(AppRoute.Listen::class) != true) {
                return@LaunchedEffect
            }
            if (EntryFlowDecider.shouldShowGrandfatherThanks(isGrandfathered = true, alreadyShown = thanksShown)) {
                // Navigate first, then persist "shown": if the write below fails (or the
                // process dies before it runs), the thank-you simply shows again at the next
                // start instead of never showing at all.
                navController.navigate(AppRoute.Premium)
                runCatching { graph.userPreferences.setGrandfatherThanksShown(true) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        println("AppScaffold: saving grandfatherThanksShown failed: ${it.message}")
                    }
            }
            return@LaunchedEffect
        }
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
                    // Release 1.3.0 Task 7d: the daily-bird notification's "Lyssna efter den".
                    "audio" -> {
                        navController.navigate(AppRoute.AudioScan) { launchSingleTop = true }
                    }
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
    // Release 1.3.0 Task 7d: today's bird for the hero, the strips and the tab dot. Reloaded at every
    // start/return to the foreground and, while the app stays visible, just after each midnight, so a
    // new date (new bird, dot back) shows without a restart.
    LifecycleStartEffect(graph) {
        val job = scope.launch { graph.dailyBirdTracker.refreshNowAndAtMidnight() }
        onStopOrDispose { job.cancel() }
    }
    val dailyBirdDot by graph.dailyBirdTracker.showTabDot.collectAsState(initial = false)
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
    // Onboarding-replayen och Premium (köpskärm + tack-skärm, båda mörk mossa) är uppslukande
    // helskärms-vyer — dölj bottenflikarna där (övriga detaljskärmar behåller dem, som tidigare).
    val hideBottomBar =
        bottomBarEntry?.destination?.hasRoute(AppRoute.OnboardingReplay::class) == true ||
            bottomBarEntry?.destination?.hasRoute(AppRoute.Premium::class) == true
    // Plan 3 Task 6: PhotoHero screens draw up behind the status bar; icons follow what is under it.
    val heroRoute = bottomBarEntry?.destination?.isHeroRoute() == true
    val statusBarBackdrop = remember { StatusBarBackdrop() }
    StatusBarIcons(heroRoute = heroRoute, backdrop = statusBarBackdrop)
    // Release 1.3.0 Task 7g: the Premium screens (purchase and thank-you) are dark moss down to
    // the bottom edge, where three-button navigation draws its buttons; everywhere else that edge
    // is the paper bottom bar (or the paper intro replay).
    PlatformNavigationBarIcons(lightIcons = bottomBarEntry?.destination?.hasRoute(AppRoute.Premium::class) == true)
    // The bottom bar already pads the navigation bar; with it hidden the screen pads it itself.
    val navBarsHandledByBottomBar = if (hideBottomBar) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars
    Scaffold(
        bottomBar = { if (!hideBottomBar) BottomNavBar(navController, dailyBirdDot = dailyBirdDot) },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> CaveatToast(data) } },
        // Each screen handles the status bar itself (BelowStatusBar, or a PhotoHero drawn behind
        // it). Bottom: the bottom bar pads the navigation bar; with it hidden (Premium, intro
        // replay) the screen pads it itself. The old systemBars default left a paper-coloured
        // band under the dark Premium screens (Plan 2 final review).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        CompositionLocalProvider(LocalStatusBarBackdrop provides statusBarBackdrop) {
            NavHost(
                navController = navController,
                startDestination = AppRoute.Listen,
                modifier =
                    Modifier
                        .padding(bottom = padding.calculateBottomPadding())
                        // Stop nested Scaffolds (JournalScaffold) from padding the navigation
                        // bar a second time when the bottom bar has already done it.
                        .consumeWindowInsets(navBarsHandledByBottomBar),
            ) {
                appDestinations(
                    graph = graph,
                    navController = navController,
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                    dismissToast = dismissToast,
                    welcomeToast = welcomeToast,
                    isEarlyMember = isEarlyMember,
                    effectivePremiumActive = effectivePremiumActive,
                    showPremiumTeaser = showPremiumTeaser,
                )
            }
        }
    }
}

// LongMethod: this is AppScaffold's own NavHost builder, extracted here only to keep
// AppScaffold itself under the threshold (Plan 3 Task 6); it is one long flat list of
// `composable<Route> { ... }` blocks, not meaningfully splittable further. LongParameterList:
// every parameter is something the extracted destinations need from AppScaffold's own
// composition (graph/navController/scope/toasts/premium flags) — bundling them into a data
// class would just move the same parameter count one level down.
@Suppress("LongMethod", "LongParameterList")
private fun NavGraphBuilder.appDestinations(
    graph: AppGraph,
    navController: NavHostController,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    dismissToast: String,
    welcomeToast: String,
    isEarlyMember: Boolean,
    effectivePremiumActive: Boolean,
    showPremiumTeaser: Boolean,
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
    // Release 1.3.0 Task 7b: the back arrows on Scan, Photo-ID and audio ID (AudioScan) take one
    // step back, like the system gesture (they used to jump straight to the Identify tab, so
    // Scan → Photo-ID → arrow skipped Scan while the gesture went back to it).
    composable<AppRoute.Scan> { entry ->
        BelowStatusBar {
            ScanScreenHost(
                graph = graph,
                onPhotoAnalyzeClick = { navController.navigate(AppRoute.PhotoAnalyze) },
                onFrozen = { sourceJson, capturedAtMs ->
                    navController.navigate(AppRoute.MatchResult(sourceJson, capturedAtMs))
                },
                onBack = { navController.popIfTop(entry) },
            )
        }
    }
    composable<AppRoute.PhotoAnalyze> { entry ->
        BelowStatusBar {
            se.birdy.app.ui.photoanalyze.PhotoAnalyzeHost(
                graph = graph,
                onLoaded = { sourceJson, capturedAtMs ->
                    navController.navigate(AppRoute.MatchResult(sourceJson, capturedAtMs)) {
                        popUpTo(AppRoute.Scan) { inclusive = false }
                    }
                },
                onBack = { navController.popIfTop(entry) },
            )
        }
    }
    composable<AppRoute.MatchResult> { entry ->
        val route = entry.toRoute<AppRoute.MatchResult>()
        val vm =
            remember(graph, route) {
                graph.matchResultViewModel(route.sourceJson, route.capturedAtMs)
            }
        MatchResultScreen(
            viewModel = vm,
            onBack = { navController.popIfTop(entry) },
            locale = graph.defaultLocale,
            zone = graph.timeZone,
        )
    }
    navigation<AppRoute.Archive>(startDestination = AppRoute.ArchiveList) {
        composable<AppRoute.ArchiveList> {
            BelowStatusBar {
                val dailyBird by graph.dailyBirdTracker.state.collectAsState()
                ArchiveScreen(
                    // Scoped to this NavHost entry (release 1.3.0 Task 7g), not a `remember`: the
                    // entry stays on the back stack while a species profile covers it, so the search
                    // text, results and scroll position are still there on the way back. Keyed on
                    // the graph: a language switch recreates the activity with a new AppGraph (new
                    // locale, new billing client) while the entry's ViewModels survive, and the
                    // list must then come from the new graph (review fix I1).
                    viewModel = viewModel(key = "archive-${graph.hashCode()}") { graph.archiveViewModel() },
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
                    dailyBird = dailyBird,
                    onDailyBirdClick = { id ->
                        navController.navigate(AppRoute.SpeciesProfile(id)) { launchSingleTop = true }
                    },
                )
            }
        }
        composable<AppRoute.SpeciesProfile> { entry ->
            val route = entry.toRoute<AppRoute.SpeciesProfile>()
            SpeciesProfileScreen(
                viewModel =
                    remember(graph, route.speciesId) {
                        graph.speciesProfileViewModel(SpeciesId(route.speciesId))
                    },
                locale = graph.defaultLocale,
                onBack = { navController.popIfTop(entry) },
                onPremiumClick = { navController.navigate(AppRoute.Premium) },
                showPremiumTeaser = showPremiumTeaser,
            )
        }
    }
    composable<AppRoute.Lifelist> {
        BelowStatusBar {
            val livePreviewState =
                if (effectivePremiumActive) {
                    val seasonStatsVm = remember(graph) { graph.seasonStatsViewModel() }
                    LaunchedEffect(seasonStatsVm) { seasonStatsVm.onEnter() }
                    val state by seasonStatsVm.state.collectAsState()
                    state as? se.birdy.app.ui.stats.SeasonStatsUiState.Loaded
                } else {
                    null
                }
            val dailyBird by graph.dailyBirdTracker.state.collectAsState()
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
                dailyBird = dailyBird,
                onDailyBirdClick = { id ->
                    navController.navigate(AppRoute.SpeciesProfile(id)) { launchSingleTop = true }
                },
            )
        }
    }
    composable<AppRoute.Map> {
        BelowStatusBar {
            val mapVm = remember(graph) { graph.mapViewModel() }
            if (effectivePremiumActive) {
                se.birdy.app.ui.map.MapScreen(
                    viewModel = mapVm,
                    onPinClick = { id -> navController.navigate(AppRoute.ObservationDetail(id)) },
                    onIdentifyClick = {
                        navController.navigate(AppRoute.Listen) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            } else {
                se.birdy.app.ui.map.MapPremiumTeaser(
                    viewModel = mapVm,
                    onUpgrade = { navController.navigate(AppRoute.Premium) },
                )
            }
        }
    }
    composable<AppRoute.ObservationDetail> { entry ->
        val route = entry.toRoute<AppRoute.ObservationDetail>()
        BelowStatusBar {
            ObservationDetailScreen(
                viewModel = remember(graph, route.id) { graph.observationDetailViewModel(route.id) },
                onBack = { navController.popIfTop(entry) },
                onSpeciesClick = { id -> navController.navigate(AppRoute.SpeciesProfile(id)) },
            )
        }
    }
    composable<AppRoute.Badges> {
        BelowStatusBar {
            BadgesRoute(
                graph = graph,
                onSettingsClick = { navController.navigate(AppRoute.Settings) { launchSingleTop = true } },
                onPremiumClick = { navController.navigate(AppRoute.Premium) },
                onOpenTrophyRoom = { navController.navigate(AppRoute.TrophyRoom) { launchSingleTop = true } },
                showPremiumTeaser = showPremiumTeaser,
            )
        }
    }
    composable<AppRoute.TrophyRoom> { entry ->
        BelowStatusBar {
            TrophyRoomRoute(
                graph = graph,
                onBack = { navController.popIfTop(entry) },
            )
        }
    }
    composable<AppRoute.Settings> { entry ->
        BelowStatusBar {
            se.birdy.app.ui.settings.SettingsScreen(
                viewModel = remember(graph) { graph.settingsViewModel() },
                onBack = { navController.popIfTop(entry) },
                onPremiumClick = { navController.navigate(AppRoute.Premium) },
                onNavigateToAbout = { navController.navigate(AppRoute.About) },
                onShowIntroAgain = { navController.navigate(AppRoute.OnboardingReplay) },
                versionName = graph.versionName,
                onRequestLocationPermission = { graph.requestLocationPermission?.invoke() },
            )
        }
    }
    composable<AppRoute.About> { entry ->
        BelowStatusBar {
            se.birdy.app.ui.settings.AboutScreen(
                onBack = { navController.popIfTop(entry) },
                version = graph.versionName,
            )
        }
    }
    composable<AppRoute.OnboardingReplay> { entry ->
        BelowStatusBar {
            val vm = remember(graph) { graph.onboardingViewModel(isReplay = true) }
            val state by vm.state.collectAsState()
            when (val s = state) {
                is se.birdy.app.ui.onboarding.OnboardingUiState.Visible ->
                    se.birdy.app.ui.onboarding.OnboardingScreen(
                        state = s,
                        onPageChange = vm::setPageIndex,
                        onNameChange = vm::onNameChange,
                        onLanguageSelect = vm::selectLanguage,
                        onComplete = { navController.popIfTop(entry) },
                        isReplay = true,
                    )
                se.birdy.app.ui.onboarding.OnboardingUiState.Done -> {
                    LaunchedEffect(Unit) { navController.popIfTop(entry) }
                }
                se.birdy.app.ui.onboarding.OnboardingUiState.Loading -> Unit
            }
        }
    }
    composable<AppRoute.Premium> { entry ->
        if (isEarlyMember) {
            // Idempotent pop (mirrors onPurchaseComplete below): a double tap on
            // Continue — or PlatformBackHandler firing during the NavHost's fade —
            // must not try to pop past an already-empty stack.
            PremiumThankYouScreen(
                onClose = { navController.popBackStack(AppRoute.Premium, inclusive = true) },
            )
        } else {
            PremiumScreen(
                viewModel = remember(graph) { graph.premiumViewModel() },
                onClose = {
                    // The "find it in Settings" hint only when this tap actually closed the screen.
                    if (navController.popIfTop(entry)) {
                        scope.launch { snackbarHostState.showSnackbar(dismissToast) }
                    }
                },
                onPurchaseComplete = {
                    navController.popBackStack(AppRoute.Premium, inclusive = true)
                    scope.launch { snackbarHostState.showSnackbar(welcomeToast) }
                },
            )
        }
    }
    graph.benchmarkScreen?.let { benchmarkContent ->
        composable<AppRoute.DebugBenchmark> { entry ->
            BelowStatusBar { DebugScreen(onBack = { navController.popIfTop(entry) }, content = benchmarkContent) }
        }
    }
    graph.diagnosticsScreen?.let { diagnosticsContent ->
        composable<AppRoute.DebugDiagnostics> { entry ->
            BelowStatusBar { DebugScreen(onBack = { navController.popIfTop(entry) }, content = diagnosticsContent) }
        }
    }
    composable<AppRoute.AudioScan> { entry ->
        BelowStatusBar {
            AudioScanScreenHost(
                graph = graph,
                onNavigateToMatch = { sourceJson, capturedAtMs ->
                    navController.navigate(AppRoute.MatchResult(sourceJson, capturedAtMs)) {
                        popUpTo(AppRoute.Listen) { inclusive = false }
                    }
                },
                onBack = { navController.popIfTop(entry) },
            )
        }
    }
    composable<AppRoute.SeasonStats> { entry ->
        BelowStatusBar {
            LaunchedEffect(effectivePremiumActive) {
                if (!effectivePremiumActive) {
                    navController.popIfTop(entry)
                }
            }
            se.birdy.app.ui.stats.SeasonStatsScreen(
                viewModel = remember(graph) { graph.seasonStatsViewModel() },
                onBack = { navController.popIfTop(entry) },
            )
        }
    }
    composable<AppRoute.WeeklyRecap> { entry ->
        BelowStatusBar {
            se.birdy.app.ui.recap.RecapScreen(
                viewModel = remember(graph) { graph.weeklyRecapViewModel() },
                onOpenCamera = {
                    navController.navigate(AppRoute.Scan) { launchSingleTop = true }
                },
                onObservationClick = { id -> navController.navigate(AppRoute.ObservationDetail(id)) },
                onBack = { navController.popIfTop(entry) },
            )
        }
    }
}

/** Screens whose top is a PhotoHero drawn behind the status bar (spec §3 A2, §4.3). */
private fun NavDestination.isHeroRoute(): Boolean =
    hasRoute(AppRoute.Listen::class) ||
        hasRoute(AppRoute.MatchResult::class) ||
        hasRoute(AppRoute.SpeciesProfile::class) ||
        hasRoute(AppRoute.Premium::class)

/** The debug-build screens (benchmark, diagnostics) under the same fixed way back as the others (Task 7b). */
@Composable
private fun DebugScreen(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BackTopBar(onBack = onBack)
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

/** Non-hero screens start below the status bar, each on its own, so nothing shifts during a transition. */
@Composable
private fun BelowStatusBar(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding()) { content() }
}

/** Reads the backdrop in its own scope, so scrolling past a hero doesn't recompose AppScaffold. */
@Composable
private fun StatusBarIcons(
    heroRoute: Boolean,
    backdrop: StatusBarBackdrop,
) {
    PlatformStatusBarIcons(lightIcons = heroRoute && backdrop.anyDark)
}
