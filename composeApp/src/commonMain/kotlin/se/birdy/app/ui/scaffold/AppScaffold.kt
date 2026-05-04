package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.encyclopedia.EncyclopediaScreen
import se.birdy.app.ui.profile.SpeciesProfileScreen
import se.birdy.content.SpeciesId

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
                    EncyclopediaScreen(
                        viewModel = remember(graph) { graph.encyclopediaViewModel() },
                        onSpeciesClick = { id ->
                            navController.navigate(AppRoute.SpeciesProfile(id.raw))
                        },
                    )
                }
                composable<AppRoute.SpeciesProfile> { entry ->
                    val route = entry.toRoute<AppRoute.SpeciesProfile>()
                    SpeciesProfileScreen(
                        viewModel =
                            remember(graph, route.speciesId) {
                                graph.speciesProfileViewModel(SpeciesId(route.speciesId))
                            },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable<AppRoute.Diary> { DiaryStubScreen() }
            composable<AppRoute.Badges> { BadgesStubScreen() }
        }
    }
}
