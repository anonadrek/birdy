package se.birdy.app.ui.scaffold

import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.content.SpeciesId
import se.birdy.content.model.PhotoCredit
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 (Task 7e-2, legal review 7i-fix B): About opens the photo credits and the
 * open-source licences in the real AppScaffold, an entry opens its text, and back returns step by
 * step to About.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CreditsRoutingTest {
    @get:Rule
    val compose = createComposeRule()

    private fun openAbout(repository: FakeSpeciesRepository = FakeSpeciesRepository()): NavHostController {
        val prefs = FakeUserPreferences()
        runBlocking { prefs.setGrandfatherThanksShown(true) }
        val nav =
            compose.startAppScaffold(
                testAppGraph(prefs, installedAtMs = RoutingFixture.beforeCutoffMs, repository = repository),
            )
        compose.runOnIdle { nav.navigate(AppRoute.About) }
        compose.waitForIdle()
        return nav
    }

    private fun NavHostController.isOn(route: KClass<out AppRoute>) =
        compose.runOnIdle { assertTrue(currentDestination?.hasRoute(route) == true, "not on ${route.simpleName}") }

    private fun awaitText(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    private fun back() {
        compose.onNodeWithContentDescription("Tillbaka").performClick()
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `about opens the photo credits and back returns to about`() {
        val repository =
            FakeSpeciesRepository().apply {
                photoCreditList.value =
                    listOf(
                        PhotoCredit(
                            SpeciesId("Q25485"),
                            "Talgoxe",
                            "Parus major",
                            "hero",
                            "Q25485/hero.webp",
                            "CC0",
                            "Hobbyfotowiki",
                            "Great tit (Parus major), North Rhine-Westphalia.jpg",
                        ),
                    )
            }
        val nav = openAbout(repository)
        compose.onNodeWithText("1 artfoton från Wikimedia Commons", substring = true).assertExists()
        compose.onNodeWithText("Bildkällor").performScrollTo().performClick()
        compose.waitForIdle()
        nav.isOn(AppRoute.PhotoCredits::class)
        compose
            .onNodeWithContentDescription("Talgoxe, Huvudbild. Foto: Hobbyfotowiki. Licens: CC0. Wikimedia Commons, nedskalad.")
            .assertExists()
        back()
        nav.isOn(AppRoute.About::class)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `about opens the licences, an entry opens its text, and back returns step by step`() {
        val nav = openAbout()
        compose.onNodeWithText("Licenser för öppen källkod").performScrollTo().performClick()
        compose.waitForIdle()
        nav.isOn(AppRoute.OpenSourceLicenses::class)
        // The list and the text are read off the main thread, which waitForIdle does not wait for.
        awaitText("BirdNET-Lite")
        compose.onNodeWithText("BirdNET-Lite").performClick()
        compose.waitForIdle()
        nav.isOn(AppRoute.LicenseText::class)
        awaitText("K. Lisa Yang Center for Conservation Bioacoustics")
        back()
        nav.isOn(AppRoute.OpenSourceLicenses::class)
        back()
        nav.isOn(AppRoute.About::class)
    }

    /** A library's id has ":" and "." in it ("lib:com.android.tools:2.1.5:desugar_jdk_libs"). */
    @Test
    @Config(qualifiers = "+sv")
    fun `a library entry opens its text through the route`() {
        val nav = openAbout()
        compose.onNodeWithText("Licenser för öppen källkod").performScrollTo().performClick()
        compose.waitForIdle()
        awaitText("BirdNET-Lite")
        val id = "lib:com.android.tools:2.1.5:desugar_jdk_libs"
        compose.onNode(hasScrollToKeyAction()).performScrollToKey(id)
        compose.onNodeWithText("GPL 2.0 with the Classpath Exception", substring = true).performClick()
        compose.waitForIdle()
        nav.isOn(AppRoute.LicenseText::class)
        compose.runOnIdle { assertEquals(id, nav.currentBackStackEntry?.toRoute<AppRoute.LicenseText>()?.entryId) }
        awaitText("Source code: https://github.com/google/desugar_jdk_libs/tree/")
    }
}
