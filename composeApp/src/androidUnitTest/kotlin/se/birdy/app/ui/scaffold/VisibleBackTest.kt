package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.Species
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import se.birdy.ml.Classification
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ScanSource
import se.birdy.ml.ScanSourceSerialization
import se.birdy.ml.toSerial
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7b (Albin: "bakåtpilen fattas i vissa sektioner"), in the REAL AppScaffold:
 * every pushed screen, and each of its loading, empty and error states, has a visible way back
 * ("Tillbaka", or "Stäng" where the screen closes). Where the screen scrolls, the control is
 * still in view after scrolling every list on it to the bottom. One tap takes exactly one step
 * back, and a double tap (both taps before the NavHost has recomposed, as during the exit fade)
 * still ends exactly one step back, never on the screen below that or on an empty NavHost.
 *
 * A short phone (360x560dp, minus the 72dp bottom bar) so the screens that used to lose their
 * button when scrolled are taller than the screen; the helper checks they really did scroll.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w360dp-h560dp-xxhdpi")
class VisibleBackTest {
    @get:Rule
    val compose = createComposeRule()

    private val back = "Tillbaka"
    private val close = "Stäng"

    /** An early member who already saw the thank-you: nothing covers the start screen. */
    private fun startPrefs(): FakeUserPreferences = FakeUserPreferences().also { runBlocking { it.setGrandfatherThanksShown(true) } }

    private fun start(
        repository: SpeciesRepository = FakeSpeciesRepository.withDefaults(),
        observations: ObservationRepository = FakeObservationRepository(),
        prefs: FakeUserPreferences = startPrefs(),
        installedAtMs: Long = RoutingFixture.beforeCutoffMs,
        diagnosticsScreen: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    ): NavHostController =
        compose.startAppScaffold(
            testAppGraph(
                prefs,
                installedAtMs = installedAtMs,
                repository = repository,
                observationRepository = observations,
                diagnosticsScreen = diagnosticsScreen,
            ),
        )

    private fun NavHostController.open(vararg routes: AppRoute) {
        compose.runOnIdle { routes.forEach { navigate(it) } }
        compose.waitForIdle()
    }

    private fun NavHostController.isOn(route: KClass<*>): Boolean = currentDestination?.hasRoute(route) == true

    /**
     * Scrolls every vertical list on screen as far down as it goes. Returns whether any of them
     * had anything to scroll, so a test can tell that the screen really was taller than the phone.
     */
    private fun scrollEverythingToBottom(): Boolean {
        val lists =
            compose.onAllNodes(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        var scrolled = false
        repeat(lists.fetchSemanticsNodes().size) { i ->
            lists[i].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
            compose.waitForIdle()
            val range = lists[i].fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(range.value() >= range.maxValue() - 1f, "a list stopped at ${range.value()} of ${range.maxValue()}")
            if (range.value() > 0f) scrolled = true
        }
        return scrolled
    }

    private fun control(
        description: String,
        byText: Boolean,
    ): SemanticsNodeInteraction = if (byText) compose.onNodeWithText(description) else compose.onNodeWithContentDescription(description)

    private fun assertControlShownAtBottom(
        description: String,
        byText: Boolean = false,
        mustScroll: Boolean,
    ) {
        val scrolled = scrollEverythingToBottom()
        if (mustScroll) assertTrue(scrolled, "the screen should be taller than the phone here, or this proves nothing")
        control(description, byText).assertIsDisplayed()
    }

    private fun doubleTap(node: () -> SemanticsNodeInteraction) {
        compose.mainClock.autoAdvance = false
        node().performSemanticsAction(SemanticsActions.OnClick)
        node().performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /**
     * Opens [routes] on top of the start screen and checks the top screen's back control: in view
     * after scrolling to the bottom, one tap = exactly one step back. Then opens the screen again
     * and double taps: still exactly one step back.
     */
    private fun checkBack(
        nav: NavHostController,
        routes: List<AppRoute>,
        description: String = back,
        byText: Boolean = false,
        mustScroll: Boolean = false,
    ) {
        nav.open(*routes.toTypedArray())
        assertControlShownAtBottom(description, byText, mustScroll)
        val oneStepBack = nav.previousBackStackEntry
        control(description, byText).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertSame(oneStepBack, nav.currentBackStackEntry, "one tap should go exactly one step back") }

        nav.open(routes.last())
        val oneStepBackAgain = nav.previousBackStackEntry
        doubleTap { control(description, byText) }
        compose.runOnIdle { assertSame(oneStepBackAgain, nav.currentBackStackEntry, "a double tap should still go one step back") }
    }

    // --- Content ---------------------------------------------------------------------------------

    private val longText = "Talgoxen sjunger tidigt på våren och syns vid fågelbordet hela vintern. ".repeat(30)

    /** Talgoxe with a long text, so its profile is several screens tall. */
    private fun repositoryWithLongProfile(): FakeSpeciesRepository =
        FakeSpeciesRepository.withDefaults().apply {
            val id = SpeciesId("Q25485")
            byId.value = byId.value + (id to byId.value.getValue(id)!!.copy(description = longText))
        }

    /** A repository whose species lookups never answer: the screens stay in their loading state. */
    private class NeverAnsweringRepository : SpeciesRepository by FakeSpeciesRepository.withDefaults() {
        override fun getById(
            id: SpeciesId,
            locale: Locale,
        ): Flow<Species?> = flow { awaitCancellation() }
    }

    private fun observation(
        id: String,
        capturedAt: Instant,
        speciesId: String = "Q25485",
    ) = Observation(
        id = id,
        speciesId = speciesId,
        capturedAt = capturedAt,
        savedAt = capturedAt,
        photoPath = "/fake/$id.jpg",
        note = "",
        confidence = 0.9f,
        latitude = null,
        longitude = null,
        locationLabel = null,
    )

    private fun observationsWithOneFind(): FakeObservationRepository =
        FakeObservationRepository().apply { seedDirect(observation("obs-1", Instant.parse("2026-10-05T08:00:00Z"))) }

    /** A Match/Disambig/NoBird route for a photo scan with these results. */
    private fun matchRoute(
        vararg results: Pair<String, Float>,
        framePath: String = "",
    ): AppRoute.MatchResult {
        val source =
            ScanSource.Image(
                frameJpegPath = framePath,
                classification = Classification(results.map { (id, confidence) -> ClassificationResult(id, confidence) }),
            )
        return AppRoute.MatchResult(
            Json.encodeToString(ScanSourceSerialization.serializer(), source.toSerial()),
            RoutingFixture.now.toEpochMilliseconds(),
        )
    }

    // --- Screens whose button scrolled away --------------------------------------------------

    @Test
    fun `Settings keeps its back button in view and goes one step back`() {
        checkBack(start(), listOf(AppRoute.Settings), mustScroll = true)
    }

    @Test
    fun `the Settings list starts below the back button, so it never slides over it`() {
        val nav = start()
        nav.open(AppRoute.Settings)
        val button = compose.onNodeWithContentDescription(back).fetchSemanticsNode().touchBoundsInRoot
        val list =
            compose
                .onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(button.bottom <= list.top, "the back button reaches ${button.bottom - list.top}px into the list")
    }

    @Test
    fun `the species profile keeps its back button over the photo and the text`() {
        checkBack(start(repository = repositoryWithLongProfile()), listOf(AppRoute.SpeciesProfile("Q25485")), mustScroll = true)
    }

    @Test
    fun `a find keeps its back button in view and goes back to Mina arter`() {
        checkBack(
            start(observations = observationsWithOneFind()),
            listOf(AppRoute.Lifelist, AppRoute.ObservationDetail("obs-1")),
            mustScroll = true,
        )
    }

    @Test
    fun `the weekly recap has a back button`() {
        val now = Clock.System.now()
        val observations =
            FakeObservationRepository().apply {
                repeat(8) { seedDirect(observation("week-$it", Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - it * 1_000L))) }
            }
        checkBack(start(observations = observations), listOf(AppRoute.Lifelist, AppRoute.WeeklyRecap), mustScroll = true)
    }

    @Test
    fun `the weekly recap without finds has a back button`() {
        checkBack(start(), listOf(AppRoute.Lifelist, AppRoute.WeeklyRecap))
    }

    // --- Identify results -------------------------------------------------------------------

    @Test
    fun `Match has a back button over the photo`() {
        checkBack(start(), listOf(matchRoute("Q25485" to 0.95f)), mustScroll = true)
    }

    @Test
    fun `Match keeps a way back after Spara, when Avbryt is gone`() {
        val frame = File.createTempFile("frame", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val observations = FakeObservationRepository()
        val nav = start(observations = observations)
        nav.open(matchRoute("Q25485" to 0.95f, framePath = frame.absolutePath))
        compose.onNodeWithText("Spara observation").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(observations.allInserted.size == 1, "the find was saved")
        compose.onNodeWithText("Avbryt").assertDoesNotExist()

        // The saved state is shorter (no note field, no buttons), so it may not scroll at all.
        assertControlShownAtBottom(back, mustScroll = false)
        doubleTap { compose.onNodeWithContentDescription(back) }
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.Listen::class) && nav.previousBackStackEntry == null) }
    }

    @Test
    fun `Disambig has a back button above the candidates`() {
        checkBack(start(), listOf(matchRoute("Q25485" to 0.45f, "Q25404" to 0.40f, "Q25234" to 0.38f)), mustScroll = true)
    }

    @Test
    fun `NoBird has a back button besides Try again`() {
        checkBack(start(), listOf(matchRoute("Q25485" to 0.05f)))
    }

    @Test
    fun `a result that failed to load has a back button`() {
        val repository = FakeSpeciesRepository.withDefaults().apply { failingIds.value = setOf(SpeciesId("Q25485")) }
        val nav = start(repository = repository)
        nav.open(matchRoute("Q25485" to 0.95f))
        compose.onNodeWithText("Inga arter kunde matchas", substring = true).assertExists()
        checkBack(nav, listOf(matchRoute("Q25485" to 0.95f)))
    }

    @Test
    fun `a result still loading has a back button`() {
        checkBack(start(repository = NeverAnsweringRepository()), listOf(matchRoute("Q25485" to 0.95f)))
    }

    // --- Empty, error and loading states ---------------------------------------------------

    @Test
    fun `a species that does not exist has a back button`() {
        val nav = start()
        nav.open(AppRoute.SpeciesProfile("Q1"))
        compose.onNodeWithText("Art saknas.").assertExists()
        checkBack(nav, listOf(AppRoute.SpeciesProfile("Q1")))
    }

    @Test
    fun `a species profile still loading has a back button`() {
        val repository =
            object : SpeciesRepository by FakeSpeciesRepository.withDefaults() {
                override fun getById(
                    id: SpeciesId,
                    locale: Locale,
                ): Flow<Species?> = emptyFlow()
            }
        checkBack(start(repository = repository), listOf(AppRoute.SpeciesProfile("Q25485")))
    }

    @Test
    fun `a find that does not exist has a back button`() {
        checkBack(start(), listOf(AppRoute.Lifelist, AppRoute.ObservationDetail("missing")))
    }

    @Test
    fun `a find that failed to load has a back button`() {
        val observations =
            object : ObservationRepository by FakeObservationRepository() {
                override fun observeById(id: String): Flow<Observation?> = flow { error("database unavailable") }
            }
        checkBack(start(observations = observations), listOf(AppRoute.Lifelist, AppRoute.ObservationDetail("obs-1")))
    }

    @Test
    fun `a find still loading has a back button`() {
        val observations =
            object : ObservationRepository by FakeObservationRepository() {
                override fun observeById(id: String): Flow<Observation?> = emptyFlow()
            }
        checkBack(start(observations = observations), listOf(AppRoute.Lifelist, AppRoute.ObservationDetail("obs-1")))
    }

    // --- Screens that close ----------------------------------------------------------------

    @Test
    fun `the thank-you has a close button in view at the bottom that closes it once`() {
        val prefs = FakeUserPreferences()
        val nav = start(prefs = prefs)
        compose.onNodeWithText("Du var med innan Birdy", substring = true).assertExists()
        assertControlShownAtBottom(close, mustScroll = true)
        val oneStepBack = nav.previousBackStackEntry
        compose.onNodeWithContentDescription(close).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertSame(oneStepBack, nav.currentBackStackEntry) }
        compose.onNodeWithText("Kika").assertExists()
        assertTrue(runBlocking { prefs.grandfatherThanksShown.first() }, "the thank-you shows only once")

        // Opened again from Premium: a double tap on the close button keeps the start screen.
        nav.open(AppRoute.Premium)
        doubleTap { compose.onNodeWithContentDescription(close) }
        compose.runOnIdle { assertSame(oneStepBack, nav.currentBackStackEntry) }
        compose.onNodeWithText("Kika").assertExists()
    }

    @Test
    fun `the intro replay has a full size close button that goes back to Settings`() {
        val nav = start()
        nav.open(AppRoute.Settings)
        val settings = nav.currentBackStackEntry
        nav.open(AppRoute.OnboardingReplay)
        compose.onNodeWithText(close).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(close).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertSame(settings, nav.currentBackStackEntry) }

        nav.open(AppRoute.OnboardingReplay)
        doubleTap { compose.onNodeWithText(close) }
        compose.runOnIdle { assertSame(settings, nav.currentBackStackEntry) }
    }

    // --- Arrow and system gesture do the same -------------------------------------------------

    @Test
    fun `the arrow on Photo-ID opened from Scan goes back to Scan, like the gesture`() {
        val nav = start()
        nav.open(AppRoute.Scan, AppRoute.PhotoAnalyze)
        val scan = nav.previousBackStackEntry
        assertTrue(scan?.destination?.hasRoute(AppRoute.Scan::class) == true)
        compose.onNodeWithContentDescription(back).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertSame(scan, nav.currentBackStackEntry) }

        compose.onNodeWithContentDescription(back).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.Listen::class)) }
    }

    @Test
    fun `the photo model loader on Scan has a back button`() {
        val controllable = ControllableClassifierBootstrap(succeedOnAttempt = 2)
        val prefs = startPrefs()
        runBlocking { prefs.setHasSeenOnboarding(true) }
        val nav =
            compose.startAppGate(
                testAppGraph(prefs, RoutingFixture.beforeCutoffMs, classifierBootstrap = controllable.bootstrap),
            )
        nav.open(AppRoute.Scan)
        val hold = controllable.armHoldForNextBuild()
        compose.onNodeWithText("Försök igen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Förbereder fältboken…").assertExists()

        compose.onNodeWithContentDescription(back).assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.Listen::class)) }
        compose.runOnIdle { hold.complete(Unit) }
    }

    @Test
    fun `a debug screen has a back button`() {
        val diagnostics: @androidx.compose.runtime.Composable () -> Unit = {
            Column(Modifier.verticalScroll(rememberScrollState())) { repeat(80) { Text("rad $it") } }
        }
        checkBack(start(diagnosticsScreen = diagnostics), listOf(AppRoute.DebugDiagnostics), mustScroll = true)
    }

    // --- Which tab is marked -------------------------------------------------------------------

    private fun assertTabSelected(label: String) {
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertExists()
    }

    @Test
    fun `the weekly recap and a find from Mina arter keep the Mina arter tab marked`() {
        val nav = start(observations = observationsWithOneFind())
        nav.open(AppRoute.Lifelist, AppRoute.WeeklyRecap)
        assertTabSelected("Mina arter")
        nav.open(AppRoute.ObservationDetail("obs-1"))
        assertTabSelected("Mina arter")
    }

    @Test
    fun `a find opened from the map keeps the map tab marked`() {
        // A free user (the map tab shows its teaser), with the paywalls already behind them.
        val prefs = FakeUserPreferences()
        runBlocking {
            prefs.setPostOnboardingPremiumShown(true)
            prefs.setPremiumModalLastShownAt(RoutingFixture.now.toEpochMilliseconds())
        }
        val nav = start(observations = observationsWithOneFind(), prefs = prefs, installedAtMs = RoutingFixture.afterCutoffMs)
        nav.open(AppRoute.Map, AppRoute.ObservationDetail("obs-1"))
        assertTabSelected("Karta")
    }

    @Test
    fun `the Mina arter tab on a recap opened from its notification switches to Mina arter`() {
        val nav = start()
        nav.open(AppRoute.WeeklyRecap)
        assertTabSelected("Mina arter")
        compose.onNodeWithText("Mina arter").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.Lifelist::class)) }
    }

    // --- Tapping the tab you are already in (Task 7b review) ---------------------------------

    private fun tapTab(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `Uppslagsverk on a species opened from the list goes back to the list`() {
        val nav = start()
        nav.open(AppRoute.Archive)
        val list = nav.currentBackStackEntry
        nav.open(AppRoute.SpeciesProfile("Q25485"))
        tapTab("Uppslagsverk")
        compose.runOnIdle { assertSame(list, nav.currentBackStackEntry) }
    }

    @Test
    fun `Uppslagsverk on a species brought back from another tab goes back to the list`() {
        // What the emulator showed: the tab restored the encyclopedia with a species open, and
        // the next tap on Uppslagsverk jumped to Identifiera.
        val nav = start(repository = repositoryWithLongProfile())
        tapTab("Uppslagsverk")
        nav.open(AppRoute.SpeciesProfile("Q25485"))
        tapTab("Mina arter")
        tapTab("Uppslagsverk")
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.SpeciesProfile::class), "the tab brought the species back") }

        tapTab("Uppslagsverk")
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.ArchiveList::class)) }
    }

    @Test
    fun `Uppslagsverk on a species opened from Identifiera opens the list`() {
        val nav = start()
        // Today's bird on Identifiera opens the species straight away, without the list.
        nav.open(AppRoute.SpeciesProfile("Q25485"))
        tapTab("Uppslagsverk")
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.ArchiveList::class)) }
        compose.onNodeWithText("Talgoxe").assertDoesNotExist()
        // The list took the species' place: back goes to Identifiera.
        compose.runOnIdle { assertTrue(nav.previousBackStackEntry?.destination?.hasRoute(AppRoute.Listen::class) == true) }
    }

    @Test
    fun `Uppslagsverk on the list stays on the list`() {
        val nav = start()
        nav.open(AppRoute.Archive)
        val list = nav.currentBackStackEntry?.id
        tapTab("Uppslagsverk")
        compose.runOnIdle {
            assertTrue(nav.isOn(AppRoute.ArchiveList::class))
            assertTrue(nav.currentBackStackEntry?.id == list, "the same list, scroll and search kept")
        }
    }

    @Test
    fun `the other tabs still go back to their own first screen`() {
        val nav = start(observations = observationsWithOneFind())
        nav.open(AppRoute.Scan)
        tapTab("Identifiera")
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.Listen::class)) }

        nav.open(AppRoute.Lifelist)
        val lifelist = nav.currentBackStackEntry
        nav.open(AppRoute.ObservationDetail("obs-1"))
        tapTab("Mina arter")
        compose.runOnIdle { assertSame(lifelist, nav.currentBackStackEntry) }

        tapTab("Märken")
        val badges = nav.currentBackStackEntry
        nav.open(AppRoute.TrophyRoom)
        tapTab("Märken")
        compose.runOnIdle { assertSame(badges, nav.currentBackStackEntry) }

        tapTab("Mina arter")
        compose.runOnIdle { assertTrue(nav.isOn(AppRoute.Lifelist::class)) }
    }
}
