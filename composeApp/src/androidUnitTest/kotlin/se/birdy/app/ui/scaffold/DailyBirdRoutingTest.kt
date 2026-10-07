package se.birdy.app.ui.scaffold

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.bootstrap.BadgeVersionStore
import se.birdy.app.data.premium.PurchaseResult
import se.birdy.app.di.AppGraph
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeCameraSource
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeDailyBirdHistoryRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.ui.audio.FakeStreamingRecorder
import se.birdy.app.ui.audio.WaveformRendererApi
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.dailybird.SeasonTag
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import se.birdy.ml.AudioClassifierMode
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.ClassifierMode
import se.birdy.ml.FakeAudioClassifier
import se.birdy.ml.FakeBirdClassifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7d in the REAL AppScaffold: today's bird on the Identify hero, the tab dot
 * until it is opened, the strips on Mina arter and Uppslagsverk, and the notification's deep links
 * (`birdy://species/<id>` for "Läs om arten", the new `birdy://audio` for "Lyssna efter den").
 * Today is Tuesday 6 October 2026 in Stockholm; the daily bird is Talgoxe (Q25485).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DailyBirdRoutingTest {
    @get:Rule
    val compose = createComposeRule()

    private val prefs = FakeUserPreferences()
    private val history = FakeDailyBirdHistoryRepository()
    private val deepLinks = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
    private val dot = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Dagens fågel väntar")

    private fun graph(
        // A paying subscriber by default: no automatic paywall covers the start screen.
        premium: PremiumState = PremiumState.Active(PremiumTier.YEARLY, NOW),
        premiumOverride: PremiumState? = null,
        isGrandfathered: Boolean = false,
    ): AppGraph =
        AppGraph(
            repository = FakeSpeciesRepository.withDefaults(),
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
            premiumRepository = FakePremiumRepository(premium),
            premiumOverride = premiumOverride,
            isGrandfathered = isGrandfathered,
            clock = FakeClock(NOW),
            timeZone = TimeZone.of("Europe/Stockholm"),
            launchPurchase = { PurchaseResult.UserCancelled },
            formattedPricesFlow =
                MutableStateFlow(
                    se.birdy.app.data.premium
                        .FormattedPrices(),
                ),
            selectDailyBird = { DailyBird("Q25485", SeasonTag.PRESENT) },
            dailyBirdHistory = history,
            deepLinkFlow = deepLinks,
            audioClassifierProvider = { FakeAudioClassifier() to AudioClassifierMode.DEMO },
            audioStorageDir = { RuntimeEnvironment.getApplication().cacheDir.absolutePath },
            audioRecorderFactory = { FakeStreamingRecorder() },
            waveformRendererFactory = {
                object : WaveformRendererApi {
                    override suspend fun renderWaveformPng(
                        pcm: ShortArray,
                        outPath: String,
                    ): String = outPath

                    override suspend fun encodeOpus(
                        pcm: ShortArray,
                        outPath: String,
                    ): String? = null
                }
            },
        )

    private fun NavHostController.isOn(route: kotlin.reflect.KClass<out AppRoute>): Boolean = currentDestination?.hasRoute(route) == true

    private fun NavHostController.openedSpecies(): String? =
        currentBackStackEntry
            ?.takeIf { it.destination.hasRoute(AppRoute.SpeciesProfile::class) }
            ?.toRoute<AppRoute.SpeciesProfile>()
            ?.speciesId

    @Test
    @Config(qualifiers = "+sv")
    fun `identify shows todays bird and the tab dot until the bird is opened`() {
        val nav = compose.startAppScaffold(graph())
        compose.onNodeWithText("DAGENS FÅGEL · TIS 6\u00A0OKT", useUnmergedTree = true).assertExists()
        compose.onNode(dot).assertExists()
        assertEquals("Q25485", history.recorded[NOW_DATE], "start-up records the bird so a save can match it")

        compose.onNodeWithText("Läs om arten").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Q25485", nav.openedSpecies()) }
        assertEquals("2026-10-06", runBlocking { prefs.dailyBirdOpenedDate.first() })
        compose.onNode(dot).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the dot stays gone on a later start the same day`() {
        runBlocking { prefs.setDailyBirdOpenedDate("2026-10-06") }
        compose.startAppScaffold(graph())
        compose.onNodeWithText("Läs om arten").assertExists()
        compose.onNode(dot).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the dot is back when the bird was last opened on an earlier day`() {
        runBlocking { prefs.setDailyBirdOpenedDate("2026-10-05") }
        compose.startAppScaffold(graph())
        compose.onNode(dot).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the hero button listen for it opens audio id`() {
        val nav = compose.startAppScaffold(graph())
        compose.onNodeWithText("Lyssna efter den").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.AudioScan::class)) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the notification read about it link opens the profile and clears the dot`() {
        val nav = compose.startAppScaffold(graph())
        deepLinks.tryEmit("birdy://species/Q25485")
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Q25485", nav.openedSpecies()) }
        compose.onNode(dot).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the notification listen for it link opens audio id`() {
        val nav = compose.startAppScaffold(graph())
        deepLinks.tryEmit("birdy://audio")
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.AudioScan::class)) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the strip on mina arter opens todays bird`() {
        val nav = compose.startAppScaffold(graph())
        compose.onNodeWithText("Mina arter").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Dagens fågel: Talgoxe. Inte fångad idag, 0 av 3 dagar.").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Q25485", nav.openedSpecies()) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a double tap on the strip opens one profile`() {
        val nav = compose.startAppScaffold(graph())
        compose.onNodeWithText("Mina arter").performClick()
        compose.waitForIdle()
        val strip = compose.onNodeWithContentDescription("Dagens fågel: Talgoxe. Inte fångad idag, 0 av 3 dagar.")
        compose.runOnIdle {
            // Two taps before the first navigation has recomposed: the same click handler twice.
            val click = strip.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            click()
            click()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val profiles = nav.currentBackStack.value.count { it.destination.hasRoute(AppRoute.SpeciesProfile::class) }
            assertEquals(1, profiles)
        }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the strip in uppslagsverk opens todays bird`() {
        val nav = compose.startAppScaffold(graph())
        compose.onNodeWithText("Uppslagsverk").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Dagens fågel: Talgoxe. Inte fångad idag, 0 av 3 dagar.").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Q25485", nav.openedSpecies()) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a catch earlier today shows on the hero`() {
        history.recorded[NOW_DATE] = "Q25485"
        history.matched += NOW_DATE
        compose.startAppScaffold(graph())
        compose
            .onNodeWithContentDescription("Fångad idag. Två dagar kvar till märket. 1 av 3 dagar.", useUnmergedTree = true)
            .assertExists()
    }

    // Albin 2026-10-07: Dagens fågel-jägare is a Premium badge. The challenge row stays for everyone
    // and tags the badge as Premium for users without it, from the app's effective Premium state.
    @Test
    @Config(qualifiers = "+sv")
    fun `a user without premium sees the challenge row tag the badge as premium`() {
        // The day-0 paywall and the 7-day modal already shown, so the start screen stays in view.
        runBlocking {
            prefs.setPostOnboardingPremiumShown(true)
            prefs.setPremiumModalLastShownAt(NOW.toEpochMilliseconds())
        }
        compose.startAppScaffold(graph(premium = PremiumState.Free))
        compose
            .onNodeWithContentDescription(
                "Inte fångad idag. Spara ett fynd av arten idag. 0 av 3 dagar. Premium-märke.",
                useUnmergedTree = true,
            ).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a user without premium sees the strips on mina arter and uppslagsverk tag the badge as premium`() {
        runBlocking {
            prefs.setPostOnboardingPremiumShown(true)
            prefs.setPremiumModalLastShownAt(NOW.toEpochMilliseconds())
        }
        compose.startAppScaffold(graph(premium = PremiumState.Free))
        val tagged = "Dagens fågel: Talgoxe. Inte fångad idag, 0 av 3 dagar. Premium-märke."
        compose.onNodeWithText("Mina arter").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(tagged).assertExists()
        compose.onNodeWithText("Uppslagsverk").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(tagged).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a paying subscriber sees no premium tag on the challenge row`() {
        compose.startAppScaffold(graph())
        compose
            .onNodeWithContentDescription("Inte fångad idag. Spara ett fynd av arten idag. 0 av 3 dagar.", useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithContentDescription("Premium-märke", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `an early user with lifetime premium sees no premium tag on the challenge row`() {
        runBlocking { prefs.setGrandfatherThanksShown(true) }
        compose.startAppScaffold(
            graph(
                premium = PremiumState.Free,
                premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, NOW),
                isGrandfathered = true,
            ),
        )
        compose
            .onNodeWithContentDescription("Inte fångad idag. Spara ett fynd av arten idag. 0 av 3 dagar.", useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithContentDescription("Premium-märke", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-06T06:00:00Z")
        val NOW_DATE = kotlinx.datetime.LocalDate(2026, 10, 6)
    }
}
