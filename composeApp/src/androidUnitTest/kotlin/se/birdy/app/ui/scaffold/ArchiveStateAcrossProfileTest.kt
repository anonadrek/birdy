package se.birdy.app.ui.scaffold

import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7g item 2, in the REAL AppScaffold: the encyclopedia's search field, its
 * results and the list's scroll position are still there after opening a species and going back.
 * The ArchiveViewModel used to live in a `remember` inside the list's NavHost entry, so it was
 * thrown away (search cleared, list back at the top) every time the profile covered it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ArchiveStateAcrossProfileTest {
    @get:Rule
    val compose = createComposeRule()

    private fun summary(
        id: String,
        name: String,
    ) = SpeciesSummary(
        id = SpeciesId(id),
        name = name,
        scientificName = "Testus $name",
        abundance = Abundance.ALLMÄN,
        heroImagePath = null,
        family = "Paridae",
        familySv = "Mesfåglar",
    )

    /**
     * Lets the ViewModel's 250 ms search debounce run and Compose settle, a few rounds: a freshly
     * created ViewModel needs one round to subscribe and another for the debounced first search.
     */
    private fun settle() {
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
            compose.waitForIdle()
        }
    }

    /** The species list, not the horizontal group pills (also scrollable). */
    private fun speciesList() =
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))

    private fun NavHostController.isOn(route: kotlin.reflect.KClass<*>): Boolean = currentDestination?.hasRoute(route) == true

    @Test
    @Config(qualifiers = "+sv")
    fun `search text and scroll position survive opening a species and going back`() {
        val repo = FakeSpeciesRepository.withDefaults()
        // Talgoxe is the last of 40 results, so reaching it needs a scroll.
        repo.searchResults.value =
            (1..39).map { summary("Q9000$it", "Taa ${it.toString().padStart(2, '0')}") } +
            summary("Q25485", "Talgoxe")
        val prefs = FakeUserPreferences()
        // An early member who already saw the thank-you: no Premium screen covers the start.
        runBlocking { prefs.setGrandfatherThanksShown(true) }
        val nav = compose.startAppScaffold(testAppGraph(prefs, RoutingFixture.beforeCutoffMs, repository = repo))

        compose.runOnIdle { nav.navigate(AppRoute.Archive) }
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("tal")
        settle()
        speciesList().performScrollToNode(hasText("Talgoxe"))
        compose.onNodeWithText("Talgoxe").performClick()
        settle()
        assertTrue(nav.isOn(AppRoute.SpeciesProfile::class), "the species profile is open")

        compose.onNodeWithContentDescription("Tillbaka").performClick()
        settle()

        assertTrue(nav.isOn(AppRoute.ArchiveList::class), "back on the encyclopedia")
        // Still scrolled down to it: no performScrollToNode before this check.
        compose.onNodeWithText("Talgoxe").assertIsDisplayed()
        // The search field (scrolled out of view above) still holds the query.
        speciesList().performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).assert(hasText("tal"))
    }

    /** Answers every search with Talgoxe, named in the locale it is asked for. */
    private inner class LocalizedRepository : se.birdy.content.SpeciesRepository by FakeSpeciesRepository.withDefaults() {
        override fun search(
            query: String,
            locale: se.birdy.content.Locale,
            filters: se.birdy.content.SpeciesFilter,
        ): kotlinx.coroutines.flow.Flow<List<SpeciesSummary>> =
            kotlinx.coroutines.flow.flowOf(
                listOf(summary("Q25485", if (locale == se.birdy.content.Locale.EN) "Great Tit" else "Talgoxe")),
            )
    }

    // Review fix I1: a language switch recreates the activity, which builds a new AppGraph (new
    // locale, new billing client) while the nav entries and their ViewModels survive. The
    // encyclopedia's ViewModel must come from the new graph, or the list keeps the old language.
    @Test
    @Config(qualifiers = "+en")
    fun `a new app graph gives the encyclopedia a new view model in the new language`() {
        val repo = LocalizedRepository()
        val prefs = FakeUserPreferences()
        runBlocking { prefs.setGrandfatherThanksShown(true) }
        val swedish = testAppGraph(prefs, RoutingFixture.beforeCutoffMs, repository = repo)
        val english =
            testAppGraph(prefs, RoutingFixture.beforeCutoffMs, repository = repo, defaultLocale = se.birdy.content.Locale.EN)
        val graph = mutableStateOf(swedish)
        se.birdy.app.testing
            .attachComposeResourcesContext()
        lateinit var nav: NavHostController
        compose.setContent {
            nav = androidx.navigation.compose.rememberNavController()
            se.birdy.app.ui.theme
                .BirdyTheme { AppScaffold(graph = graph.value, navController = nav) }
        }
        compose.runOnIdle { nav.navigate(AppRoute.Archive) }
        settle()
        speciesList().performScrollToNode(hasText("Talgoxe"))

        compose.runOnIdle { graph.value = english }
        settle()

        speciesList().performScrollToNode(hasText("Great Tit"))
        compose.onNodeWithText("Talgoxe").assertDoesNotExist()
    }
}
