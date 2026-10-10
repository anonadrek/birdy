package se.birdy.app.ui.scaffold

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.domain.observation.Observation
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7j review: the Sunday 18:00 notification describes that week, and its link
 * names it (`birdy://recap?week=2026-W41`). Tapped after midnight it must still open that week,
 * not the new, empty one; Mina arter's recap card keeps opening the current week. In the real
 * AppScaffold, with the graph's clock on Monday 12 October 2026 at 00:30 in Stockholm.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class RecapNotificationRoutingTest {
    @get:Rule
    val compose = createComposeRule()

    /** Monday 12 October 2026, 00:30 in Stockholm (summer time, UTC+2). */
    private val mondayAfterMidnight = Instant.parse("2026-10-11T22:30:00Z")

    /** A Talgoxe on Sunday 11 October, week 41: the week the notification described. */
    private fun sundayFind(): FakeObservationRepository =
        FakeObservationRepository().apply {
            seedDirect(
                Observation(
                    id = "sunday",
                    speciesId = "Q25485",
                    capturedAt = Instant.parse("2026-10-11T08:00:00Z"),
                    savedAt = Instant.parse("2026-10-11T08:00:00Z"),
                    photoPath = "/fake/sunday.jpg",
                    note = "",
                    confidence = 0.9f,
                    latitude = null,
                    longitude = null,
                    locationLabel = null,
                ),
            )
        }

    private fun start(links: MutableSharedFlow<String>): NavHostController =
        compose.startAppScaffold(
            testAppGraph(
                FakeUserPreferences().also { runBlocking { it.setGrandfatherThanksShown(true) } },
                installedAtMs = RoutingFixture.beforeCutoffMs,
                repository = FakeSpeciesRepository.withDefaults(),
                observationRepository = sundayFind(),
                deepLinks = links,
                clock = FakeClock(mondayAfterMidnight),
                timeZone = TimeZone.of("Europe/Stockholm"),
            ),
        )

    private fun NavHostController.recapWeek(): String? =
        currentBackStackEntry
            ?.takeIf { it.destination.hasRoute(AppRoute.WeeklyRecap::class) }
            ?.toRoute<AppRoute.WeeklyRecap>()
            ?.week

    private fun assertShowsLastWeeksFind() {
        compose.onNodeWithText("FÄLTRAPPORT · VECKA\u00A041").assertExists()
        compose.onNodeWithContentDescription("1 fynd, 1 ny art, 1 vecka i rad.").assertExists()
        compose.onNodeWithContentDescription("söndag 11 oktober, 1 fynd").assertExists()
    }

    @Test
    fun `the notification tapped after midnight opens the week it described`() {
        val links = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val nav = start(links)
        compose.runOnIdle { assertTrue(links.tryEmit("birdy://recap?week=2026-W41")) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("2026-W41", nav.recapWeek()) }
        assertShowsLastWeeksFind()
    }

    @Test
    fun `the notification opens its week on a cold start too`() {
        // MainActivity's flow replays the link that started the app until AppScaffold collects it.
        val links = MutableSharedFlow<String>(replay = 1)
        links.tryEmit("birdy://recap?week=2026-W41")
        val nav = start(links)
        compose.runOnIdle { assertEquals("2026-W41", nav.recapWeek()) }
        assertShowsLastWeeksFind()
    }

    @Test
    fun `an older link without a week opens the current week`() {
        val links = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val nav = start(links)
        compose.runOnIdle { assertTrue(links.tryEmit("birdy://recap")) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(null, nav.recapWeek()) }
        compose.onNodeWithText("FÄLTRAPPORT · VECKA\u00A042").assertExists()
        compose.onNodeWithText("lugn").assertExists()
    }

    @Test
    fun `the card in Mina arter still opens the current week`() {
        val links = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val nav = start(links)
        compose.runOnIdle { assertTrue(links.tryEmit("birdy://recap?week=2026-W41")) }
        compose.waitForIdle()
        assertShowsLastWeeksFind()

        // Mina arter's tab, then its recap card.
        compose.onNode(hasText("Mina arter") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Veckans uppslag").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(null, nav.recapWeek()) }
        compose.onNodeWithText("FÄLTRAPPORT · VECKA\u00A042").assertExists()
        compose.onNodeWithText("lugn").assertExists()
    }
}
