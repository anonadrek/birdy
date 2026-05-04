package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import se.birdy.app.di.AppGraph

@Composable
fun AppScaffold(graph: AppGraph) {
    val navController = rememberNavController()
    Scaffold(bottomBar = { BottomNavBar(navController) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Encyclopedia,
            modifier = Modifier.padding(padding),
        ) {
            composable<AppRoute.Scan> { ScanStubScreen() }
            navigation<AppRoute.Encyclopedia>(startDestination = AppRoute.EncyclopediaList) {
                composable<AppRoute.EncyclopediaList> {
                    Text("Encyclopedia placeholder — Task 3 wires this") // TASK-3 REPLACE
                }
                composable<AppRoute.SpeciesProfile> { entry ->
                    val route = entry.toRoute<AppRoute.SpeciesProfile>()
                    Text("Profile for ${route.speciesId} — Task 7 wires this") // TASK-7 REPLACE
                }
            }
            composable<AppRoute.Diary> { DiaryStubScreen() }
            composable<AppRoute.Badges> { BadgesStubScreen() }
        }
    }
}
