package se.birdy.app.ui.scaffold

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Plan 3 Task 2: popIfTop pops a back-stack entry at most once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PopIfTopTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private lateinit var settingsEntry: NavBackStackEntry

    /** Back stack: Listen, Settings, About (About on top). */
    private fun startWithThreeScreens() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = AppRoute.Listen) {
                composable<AppRoute.Listen> { BasicText("listen") }
                composable<AppRoute.Settings> { BasicText("settings") }
                composable<AppRoute.About> { BasicText("about") }
            }
        }
        compose.runOnIdle {
            nav.navigate(AppRoute.Settings)
            settingsEntry = checkNotNull(nav.currentBackStackEntry)
            nav.navigate(AppRoute.About)
        }
        compose.waitForIdle()
    }

    @Test
    fun `plain popBackStack twice also pops the screen underneath`() {
        startWithThreeScreens()
        compose.runOnIdle {
            nav.popBackStack()
            nav.popBackStack()
            assertTrue(nav.currentDestination?.hasRoute(AppRoute.Listen::class) == true)
        }
    }

    @Test
    fun `popIfTop twice with the same entry pops only that entry`() {
        startWithThreeScreens()
        compose.runOnIdle {
            val about = checkNotNull(nav.currentBackStackEntry)
            assertTrue(nav.popIfTop(about))
            assertFalse(nav.popIfTop(about))
            assertTrue(nav.currentDestination?.hasRoute(AppRoute.Settings::class) == true)
        }
    }

    @Test
    fun `popIfTop ignores an entry that is not on top`() {
        startWithThreeScreens()
        compose.runOnIdle {
            assertFalse(nav.popIfTop(settingsEntry))
            assertTrue(nav.currentDestination?.hasRoute(AppRoute.About::class) == true)
        }
    }
}
