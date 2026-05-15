package se.birdy.app.ui.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperBottomBar
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
                    AppRoute.MatchResult::class,
                ),
        ),
        TabSpec(AppRoute.Archive, Res.string.tab_archive, Icons.AutoMirrored.Filled.LibraryBooks),
        TabSpec(AppRoute.Lifelist, Res.string.tab_lifelist, Icons.Outlined.CollectionsBookmark),
        TabSpec(AppRoute.Badges, Res.string.tab_badges, Icons.Filled.Stars),
    )

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PaperBottomBar)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(72.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in tabs) {
            val selected =
                backStackEntry?.destination?.parentChain()?.any { dest ->
                    tab.ownedRoutes.any { dest.hasRoute(it) }
                } == true
            TabCell(
                tab = tab,
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabCell(
    tab: TabSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) AccentCopper else MarginaliaInk.copy(alpha = 0.6f)
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) AccentCopper.copy(alpha = 0.12f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp)
                .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // contentDescription = null: the Text label below is merged via mergeDescendants
        // and serves as the announcement for TalkBack.
        Icon(tab.icon, contentDescription = null, tint = color)
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(tab.label),
            color = color,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
        )
    }
}

private fun NavDestination.parentChain(): Sequence<NavDestination> = generateSequence(this) { it.parent }
