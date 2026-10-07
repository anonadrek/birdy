package se.birdy.app.ui.scaffold

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
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
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import se.birdy.ml.Classification
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ScanSource
import se.birdy.ml.ScanSourceSerialization
import se.birdy.ml.toSerial
import java.io.File
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7b review, in the REAL AppScaffold: while a find is being saved (Match's
 * "Spara observation", Disambig's "Spara som okänd") there is no way back. The arrow is disabled
 * like "Avbryt", and the system back gesture does nothing, so the two still do the same thing.
 * Leaving mid-save was the window PR #21 closed: a failure would go unseen. Once the save has
 * succeeded or failed, both work again.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class SaveInProgressBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val back = "Tillbaka"

    /** Every insert waits for [release]; [failure] makes it throw instead of saving. */
    private class HeldSaves(
        val saved: FakeObservationRepository = FakeObservationRepository(),
    ) : ObservationRepository by saved {
        val release = CompletableDeferred<Unit>()
        var failure: Throwable? = null

        override suspend fun insert(observation: Observation) {
            release.await()
            failure?.let { throw it }
            saved.insert(observation)
        }
    }

    private fun start(saves: HeldSaves): NavHostController {
        val prefs = FakeUserPreferences()
        runBlocking { prefs.setGrandfatherThanksShown(true) }
        return compose.startAppScaffold(
            testAppGraph(
                prefs,
                RoutingFixture.beforeCutoffMs,
                repository = FakeSpeciesRepository.withDefaults(),
                observationRepository = saves,
            ),
        )
    }

    private fun resultRoute(vararg results: Pair<String, Float>): AppRoute.MatchResult {
        val frame = File.createTempFile("frame", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val source =
            ScanSource.Image(
                frameJpegPath = frame.absolutePath,
                classification = Classification(results.map { (id, confidence) -> ClassificationResult(id, confidence) }),
            )
        return AppRoute.MatchResult(
            Json.encodeToString(ScanSourceSerialization.serializer(), source.toSerial()),
            RoutingFixture.now.toEpochMilliseconds(),
        )
    }

    private fun NavHostController.openOnTopOfStart(route: AppRoute): NavBackStackEntry? {
        val startEntry = currentBackStackEntry
        compose.runOnIdle { navigate(route) }
        compose.waitForIdle()
        return startEntry
    }

    private fun pressSystemBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun waitUntilArrowDisabled() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription(back) and isNotEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun NavHostController.assertOnResult() =
        compose.runOnIdle { assertTrue(currentDestination?.hasRoute(AppRoute.MatchResult::class) == true, "still on the result") }

    @Test
    fun `during Spara on Match the arrow is disabled and back does nothing, after it both work`() {
        val saves = HeldSaves()
        val nav = start(saves)
        val startEntry = nav.openOnTopOfStart(resultRoute("Q25485" to 0.95f))
        compose.onNodeWithText("Spara observation").performSemanticsAction(SemanticsActions.OnClick)
        waitUntilArrowDisabled()

        compose.onNodeWithContentDescription(back).assertIsNotEnabled()
        pressSystemBack()
        nav.assertOnResult()

        compose.runOnIdle { saves.release.complete(Unit) }
        compose.waitUntil(timeoutMillis = 5_000) { saves.saved.allInserted.size == 1 }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(back).assertIsEnabled()
        pressSystemBack()
        compose.runOnIdle { assertSame(startEntry, nav.currentBackStackEntry) }
    }

    @Test
    fun `after a failed save on Match the arrow works again`() {
        val saves = HeldSaves().apply { failure = IllegalStateException("database full") }
        val nav = start(saves)
        val startEntry = nav.openOnTopOfStart(resultRoute("Q25485" to 0.95f))
        compose.onNodeWithText("Spara observation").performSemanticsAction(SemanticsActions.OnClick)
        waitUntilArrowDisabled()
        compose.runOnIdle { saves.release.complete(Unit) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription(back) and isNotEnabled()).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Spara observation").assertIsEnabled()

        compose.onNodeWithContentDescription(back).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertSame(startEntry, nav.currentBackStackEntry) }
    }

    @Test
    fun `during Spara som okand on Disambig the arrow is disabled and back does nothing`() {
        val saves = HeldSaves()
        val nav = start(saves)
        val startEntry = nav.openOnTopOfStart(resultRoute("Q25485" to 0.45f, "Q25404" to 0.40f))
        compose.onNodeWithText("Spara som okänd", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        waitUntilArrowDisabled()

        compose.onNodeWithContentDescription(back).assertIsNotEnabled()
        pressSystemBack()
        nav.assertOnResult()

        // Once saved, Disambig goes back by itself, exactly one step.
        compose.runOnIdle { saves.release.complete(Unit) }
        compose.waitUntil(timeoutMillis = 5_000) { saves.saved.allInserted.size == 1 }
        compose.waitForIdle()
        compose.runOnIdle { assertSame(startEntry, nav.currentBackStackEntry) }
    }

    @Test
    fun `after a failed save on Disambig the gesture works again`() {
        val saves = HeldSaves().apply { failure = IllegalStateException("database full") }
        val nav = start(saves)
        val startEntry = nav.openOnTopOfStart(resultRoute("Q25485" to 0.45f, "Q25404" to 0.40f))
        compose.onNodeWithText("Spara som okänd", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        waitUntilArrowDisabled()
        compose.runOnIdle { saves.release.complete(Unit) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription(back) and isNotEnabled()).fetchSemanticsNodes().isEmpty()
        }
        nav.assertOnResult()

        pressSystemBack()
        compose.runOnIdle { assertSame(startEntry, nav.currentBackStackEntry) }
    }
}
