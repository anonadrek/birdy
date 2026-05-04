package se.birdy.app.ui.scaffold

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PhotoCamera
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

private data class TabSpec(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
)

private val tabs =
    listOf(
        TabSpec(AppRoute.Scan, "Skanna", Icons.Filled.PhotoCamera),
        TabSpec(AppRoute.Encyclopedia, "Uppslagsverk", Icons.AutoMirrored.Filled.MenuBook),
        TabSpec(AppRoute.Diary, "Dagbok", Icons.Filled.Bookmark),
        TabSpec(AppRoute.Badges, "Märken", Icons.Filled.EmojiEvents),
    )

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    NavigationBar {
        for (tab in tabs) {
            val selected =
                backStackEntry?.destination?.parentChain()?.any { dest ->
                    dest.hasRoute(tab.route::class)
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
                label = { Text(tab.label) },
            )
        }
    }
}

private fun NavDestination.parentChain(): Sequence<NavDestination> = generateSequence(this) { it.parent }
