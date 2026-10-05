package se.birdy.app.ui.scaffold

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
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
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.premium.PremiumState
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.ClassifierMode
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
): AppGraph {
    val grandfathered =
        GrandfatherPolicy.isGrandfathered(
            legacyInstallMs = installedAtMs,
            trustedFirstSeenMs = installedAtMs,
            cutoffMs = RoutingFixture.cutoffMs,
        )
    return AppGraph(
        repository = FakeSpeciesRepository(),
        classifierBootstrap =
            ClassifierBootstrap(buildClassifier = { Triple(FakeBirdClassifier(), ClassifierMode.DEMO, null) }),
        cameraSourceFactory = { FakeCameraSource() },
        observationRepository = FakeObservationRepository(),
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
    )
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
