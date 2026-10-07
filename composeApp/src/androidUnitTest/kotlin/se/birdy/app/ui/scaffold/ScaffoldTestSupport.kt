package se.birdy.app.ui.scaffold

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import se.birdy.app.bootstrap.BadgeVersionStore
import se.birdy.app.data.premium.FormattedPrices
import se.birdy.app.data.premium.PurchaseResult
import se.birdy.app.di.AppGraph
import se.birdy.app.premium.GrandfatherPolicy
import se.birdy.app.premium.PremiumOverrideResolver
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeCameraSource
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.audio.FakeStreamingRecorder
import se.birdy.app.ui.audio.WaveformRendererApi
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.content.Locale
import se.birdy.content.SpeciesRepository
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.observation.ObservationRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.ml.AudioClassifierMode
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.ClassifierMode
import se.birdy.ml.FakeAudioClassifier
import se.birdy.ml.FakeBirdClassifier

/**
 * Fixed instants for the premium routing tests. Self-consistent cutoff window
 * (beforeCutoffMs < cutoffMs < afterCutoffMs < now), independent of the shipped
 * BuildConfig.GRANDFATHER_CUTOFF_MS default (which moves with the release date, see
 * androidApp/build.gradle.kts) — these tests derive isGrandfathered from this cutoff
 * themselves via GrandfatherPolicy.isGrandfathered, not from the build constant.
 */
internal object RoutingFixture {
    val now: Instant = Instant.parse("2026-10-10T08:00:00Z")
    val cutoffMs: Long = Instant.parse("2026-10-01T22:00:00Z").toEpochMilliseconds()
    val beforeCutoffMs: Long = Instant.parse("2026-06-20T08:00:00Z").toEpochMilliseconds()
    val afterCutoffMs: Long = Instant.parse("2026-10-05T08:00:00Z").toEpochMilliseconds()
}

/**
 * A real AppGraph wired with the commonTest fakes. The premium inputs are derived the way
 * MainActivity.buildAppGraph() derives them: install time → GrandfatherPolicy → resolver.
 */
internal fun testAppGraph(
    prefs: FakeUserPreferences,
    installedAtMs: Long,
    debugSkipOverride: Boolean = false,
    backend: PremiumState = PremiumState.Free,
    prices: FormattedPrices = FormattedPrices(),
    // Release 1.3.0 Plan 3 Task 2c: overridable so AppGate/photo-model-failed tests can supply
    // a ClassifierBootstrap that starts Failed (or counts retry() attempts) instead of the
    // always-succeeds default below.
    classifierBootstrap: ClassifierBootstrap =
        ClassifierBootstrap(buildClassifier = { Triple(FakeBirdClassifier(), ClassifierMode.DEMO, null) }),
    // Release 1.3.0 Task 7g: the encyclopedia test feeds its own search results.
    repository: SpeciesRepository = FakeSpeciesRepository(),
    defaultLocale: Locale = Locale.SV,
    // Release 1.3.0 Task 7b: the back-button tests open finds, the weekly recap and a debug screen.
    observationRepository: ObservationRepository = FakeObservationRepository(),
    diagnosticsScreen: (@Composable () -> Unit)? = null,
    // Task 7b review: fakes for the audio-ID screen, so a test can open AudioScan.
    withAudio: Boolean = false,
): AppGraph {
    val grandfathered =
        GrandfatherPolicy.isGrandfathered(
            legacyInstallMs = installedAtMs,
            trustedFirstSeenMs = installedAtMs,
            cutoffMs = RoutingFixture.cutoffMs,
        )
    return AppGraph(
        repository = repository,
        classifierBootstrap = classifierBootstrap,
        cameraSourceFactory = { FakeCameraSource() },
        observationRepository = observationRepository,
        photoStorage = FakePhotoStorage(),
        badgeRepository = FakeBadgeRepository(),
        badgeCatalog = BadgeCatalog(version = 1, badges = emptyList()),
        badgeVersionStore =
            object : BadgeVersionStore {
                override var lastSeen: Int = 1
            },
        userPreferences = prefs,
        premiumRepository = FakePremiumRepository(backend),
        premiumOverride =
            PremiumOverrideResolver.resolve(
                isGrandfathered = grandfathered,
                debugSkipOverride = debugSkipOverride,
                premiumOpenForLaunch = false,
                debugForceYearly = false,
                now = RoutingFixture.now,
            ),
        isGrandfathered = grandfathered,
        clock = FakeClock(RoutingFixture.now),
        // A real Play purchase must never start from a test. AppGraph's own default would
        // mark the fake repository as purchased.
        launchPurchase = { PurchaseResult.UserCancelled },
        formattedPricesFlow = MutableStateFlow(prices),
        defaultLocale = defaultLocale,
        diagnosticsScreen = diagnosticsScreen,
        audioClassifierProvider = if (withAudio) ({ FakeAudioClassifier() to AudioClassifierMode.DEMO }) else null,
        audioStorageDir = if (withAudio) ({ System.getProperty("java.io.tmpdir") }) else null,
        audioRecorderFactory = if (withAudio) ({ FakeStreamingRecorder() }) else null,
        waveformRendererFactory = if (withAudio) ({ NoWaveformRenderer }) else null,
    )
}

/** Writes nothing: the audio-ID tests never get as far as saving a recording. */
private object NoWaveformRenderer : WaveformRendererApi {
    override suspend fun renderWaveformPng(
        pcm: ShortArray,
        outPath: String,
    ): String = outPath

    override suspend fun encodeOpus(
        pcm: ShortArray,
        outPath: String,
    ): String? = null
}

/** Composes the real AppScaffold inside BirdyTheme, waits until it has settled, returns its NavHostController. */
internal fun ComposeContentTestRule.startAppScaffold(graph: AppGraph): NavHostController {
    attachComposeResourcesContext()
    lateinit var nav: NavHostController
    setContent {
        nav = rememberNavController()
        BirdyTheme { AppScaffold(graph = graph, navController = nav) }
    }
    waitForIdle()
    return nav
}

/**
 * Composes the real AppGate (not AppScaffold directly) inside BirdyTheme, waits until it has
 * settled, returns its NavHostController. Release 1.3.0 Plan 3 Task 2c: use this (instead of
 * [startAppScaffold]) whenever a test cares about the gate's own Initializing/Failed/Ready
 * handling — AppScaffold alone never reads classifierBootstrap.state.
 */
internal fun ComposeContentTestRule.startAppGate(graph: AppGraph): NavHostController {
    attachComposeResourcesContext()
    lateinit var nav: NavHostController
    setContent {
        nav = rememberNavController()
        BirdyTheme { AppGate(graph = graph, navController = nav) }
    }
    waitForIdle()
    return nav
}

/**
 * A real [ClassifierBootstrap] whose build runs synchronously on the calling thread
 * ([Dispatchers.Unconfined] instead of the production default [Dispatchers.Default]), so
 * Robolectric tests observe [ClassifierBootstrap.state] settle deterministically — by the time
 * the constructor (or [ClassifierBootstrap.retry]) returns, the build has already run, instead
 * of racing a real background thread. [buildAttempts] counts every call to the builder (the
 * initial build, plus one per [ClassifierBootstrap.retry]); the builder fails while
 * `buildAttempts.value < succeedOnAttempt`, so e.g. `succeedOnAttempt = 2` starts Failed and
 * only succeeds after one retry().
 *
 * Release 1.3.0 Plan 3 Task 2c review fix: [holdInitialBuild] and [armHoldForNextBuild] let a
 * test suspend a build attempt mid-flight on a [CompletableDeferred], so it can observe
 * [se.birdy.ml.ClassifierBootstrapState.Initializing] on its own before completing the hold and
 * letting that attempt settle (succeed/fail per [succeedOnAttempt]) — with Unconfined otherwise
 * every build settles synchronously, so Initializing is never observable by itself.
 */
internal class ControllableClassifierBootstrap(
    succeedOnAttempt: Int,
    holdInitialBuild: Boolean = false,
) {
    private val _buildAttempts = MutableStateFlow(0)
    val buildAttempts: StateFlow<Int> = _buildAttempts.asStateFlow()

    private var pendingHold: CompletableDeferred<Unit>? = null

    /** Non-null only when constructed with `holdInitialBuild = true`; complete it to let the first build settle. */
    val initialBuildHold: CompletableDeferred<Unit>? = if (holdInitialBuild) arm() else null

    /**
     * Arms a one-shot hold for the NEXT build attempt (a later [ClassifierBootstrap.retry]).
     * Call this before triggering that retry; complete the returned [CompletableDeferred] to let
     * the attempt proceed.
     */
    fun armHoldForNextBuild(): CompletableDeferred<Unit> = arm()

    private fun arm(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { pendingHold = it }

    val bootstrap: ClassifierBootstrap =
        ClassifierBootstrap(
            buildClassifier = {
                val hold = pendingHold
                pendingHold = null
                hold?.await()
                val attempt = _buildAttempts.value + 1
                _buildAttempts.value = attempt
                if (attempt < succeedOnAttempt) {
                    error("photo model unavailable in test (attempt $attempt)")
                }
                Triple(FakeBirdClassifier(), ClassifierMode.DEMO, null)
            },
            scope = CoroutineScope(Dispatchers.Unconfined),
            buildContext = Dispatchers.Unconfined,
        )
}
