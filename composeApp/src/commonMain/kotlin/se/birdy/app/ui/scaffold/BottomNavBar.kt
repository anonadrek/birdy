package se.birdy.app.ui.scaffold

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.tab_archive
import birdy_bird_scanner.composeapp.generated.resources.tab_badges
import birdy_bird_scanner.composeapp.generated.resources.tab_lifelist
import birdy_bird_scanner.composeapp.generated.resources.tab_listen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KClass

private data class TabSpec(
    val route: AppRoute,
    val label: StringResource,
    val icon: ImageVector,
    val ownedRoutes: Set<KClass<out AppRoute>> = setOf(route::class),
)

private val tabs =
    listOf(
        TabSpec(
            route = AppRoute.Listen,
            label = Res.string.tab_listen,
            icon = Icons.Filled.Hearing,
            ownedRoutes =
                setOf(
                    AppRoute.Listen::class,
                    AppRoute.Scan::class,
                    AppRoute.PhotoAnalyze::class,
                    AppRoute.ClassificationResult::class,
                ),
        ),
        TabSpec(AppRoute.Archive, Res.string.tab_archive, Icons.AutoMirrored.Filled.LibraryBooks),
        TabSpec(AppRoute.Lifelist, Res.string.tab_lifelist, Icons.Outlined.CollectionsBookmark),
        TabSpec(AppRoute.Badges, Res.string.tab_badges, Icons.Filled.Stars),
    )

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    NavigationBar {
        for (tab in tabs) {
            val selected =
                backStackEntry?.destination?.parentChain()?.any { dest ->
                    tab.ownedRoutes.any { dest.hasRoute(it) }
                } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

private fun NavDestination.parentChain(): Sequence<NavDestination> = generateSequence(this) { it.parent }
