@file:Suppress("TooManyFunctions") // the bar, its tabs and the rules for which tab a stack belongs to.

package se.birdy.app.ui.scaffold

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import birdy_bird_scanner.composeapp.generated.resources.tab_listen_daily_bird_new
import birdy_bird_scanner.composeapp.generated.resources.tab_map
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.PaperBottomBar
import kotlin.reflect.KClass

/** Test tag on each tab's selection-dot slot (BottomNavLabelFitTest). */
internal const val SELECTED_DOT_TAG = "tab-selected-dot"

private val LABEL_MAX_FONT_SIZE = 10.sp

// Low enough that every label still fits its tab at a 2.0 system text size on a 360dp phone.
private val LABEL_MIN_FONT_SIZE = 4.sp

private val LABEL_LINE_HEIGHT = 1.2.em

// The label's share of the tab's 48dp column: 48 − icon 24 − spacers 2 + 3 − dot 4.
private val LABEL_MAX_HEIGHT = 15.dp

// Largest label font whose 1.2em line still fits LABEL_MAX_HEIGHT (15 / 1.2 = 12.5, rounded down).
private val LABEL_MAX_FONT_HEIGHT = 12.dp

private data class TabSpec(
    val route: AppRoute,
    val label: StringResource,
    val icon: ImageVector,
    val ownedRoutes: Set<KClass<out AppRoute>> = setOf(route::class),
    // The screen the tab opens on. The encyclopedia's route is its nested graph, whose first
    // screen is the list (release 1.3.0 Task 7b review: popping to the graph itself also popped
    // the graph, so the tab jumped to Identifiera).
    val rootRoute: AppRoute = route,
)

private val tabs =
    listOf(
        TabSpec(
            route = AppRoute.Listen,
            label = Res.string.tab_listen,
            icon = Icons.Filled.CenterFocusStrong,
            ownedRoutes =
                setOf(
                    AppRoute.Listen::class,
                    AppRoute.Scan::class,
                    AppRoute.PhotoAnalyze::class,
                    AppRoute.AudioScan::class,
                    AppRoute.MatchResult::class,
                ),
        ),
        TabSpec(
            route = AppRoute.Archive,
            label = Res.string.tab_archive,
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            rootRoute = AppRoute.ArchiveList,
        ),
        // Release 1.3.0 Task 7b: the weekly recap and season statistics are Mina arter's screens
        // (the recap opened from its notification marks Mina arter too, unless another tab's
        // stack is open, see markedTab).
        TabSpec(
            route = AppRoute.Lifelist,
            label = Res.string.tab_lifelist,
            icon = Icons.Outlined.CollectionsBookmark,
            ownedRoutes = setOf(AppRoute.Lifelist::class, AppRoute.WeeklyRecap::class, AppRoute.SeasonStats::class),
        ),
        TabSpec(
            route = AppRoute.Badges,
            label = Res.string.tab_badges,
            icon = Icons.Filled.Stars,
            ownedRoutes = setOf(AppRoute.Badges::class, AppRoute.TrophyRoom::class),
        ),
        TabSpec(AppRoute.Map, Res.string.tab_map, Icons.Outlined.Map),
    )

/**
 * [dailyBirdDot]: a rust dot on the Identify tab while today's Dagens fågel hasn't been opened
 * (release 1.3.0 Task 7d; AppScaffold passes DailyBirdTracker.showTabDot).
 */
@Composable
fun BottomNavBar(
    navController: NavHostController,
    dailyBirdDot: Boolean = false,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    // The back stack only changes together with its top entry, so this is worked out again
    // exactly when backStackEntry changes.
    val markedTab = remember(backStackEntry) { backStackEntry?.let { navController.markedTab(it.destination) } }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PaperBottomBar)
                .drawBehind {
                    // Centred on y = 0 a stroked line draws half outside the bar — offset by
                    // half the stroke width so the whole hairline sits inside the bar bounds.
                    val strokeWidthPx = 1.dp.toPx()
                    drawLine(
                        color = Hairline,
                        start = Offset(0f, strokeWidthPx / 2f),
                        end = Offset(size.width, strokeWidthPx / 2f),
                        strokeWidth = strokeWidthPx,
                    )
                }.windowInsetsPadding(WindowInsets.navigationBars)
                .height(72.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in tabs) {
            TabCell(
                tab = tab,
                selected = tab == markedTab,
                showDot = dailyBirdDot && tab.route == AppRoute.Listen,
                onClick = { navController.onTabClick(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabCell(
    tab: TabSpec,
    selected: Boolean,
    showDot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) AccentCopper else InkMuted
    val interactionSource = remember { MutableInteractionSource() }
    // The label follows the system text size up to what its share of the 48dp column can hold:
    // TextAutoSize only shrinks for width, so the height cap is applied to the font size itself.
    val density = LocalDensity.current
    val labelMaxFontSize = with(density) { minOf(LABEL_MAX_FONT_SIZE.toDp(), LABEL_MAX_FONT_HEIGHT).toSp() }
    val labelMinFontSize = with(density) { minOf(LABEL_MIN_FONT_SIZE.toDp(), LABEL_MAX_FONT_HEIGHT).toSp() }
    val dotDescription = stringResource(Res.string.tab_listen_daily_bird_new)
    Box(
        modifier =
            modifier
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    this.selected = selected
                    role = Role.Tab
                    if (showDot) stateDescription = dotDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        // The press ripple keeps its pill shape on a layer of its own behind the content. The tab
        // used to clip its whole content to the pill, and the pill's round ends cut into the label
        // row at larger text sizes (Release 1.3.0 Plan 3 Task 7, BottomNavLabelFitTest).
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(50))
                .indication(interactionSource, LocalIndication.current),
        )
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // contentDescription = null: the Text label below is merged via mergeDescendants
            // and serves as the announcement for TalkBack.
            Box {
                Icon(tab.icon, contentDescription = null, tint = color)
                if (showDot) NewDot(Modifier.align(Alignment.TopEnd))
            }
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = stringResource(tab.label),
                style =
                    TextStyle(
                        color = color,
                        fontSize = labelMaxFontSize,
                        // Line height in em, so it follows the (auto-sized) font instead of the
                        // system text size: this cell's budget is 72dp (bar) − 12dp (bar's own
                        // vertical padding) − 12dp (this Column's vertical padding) = 48dp for
                        // icon(24) + spacer(2) + label + spacer(3) + dot(4). An sp line height
                        // grew past it at text size 1.5+ and squeezed the selected-tab dot away
                        // (Plan 3 Task 7 review; before that, the inherited 22sp did the same).
                        lineHeight = LABEL_LINE_HEIGHT,
                        fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
                    ),
                // One line, shrunk to fit the tab's width and the label's share of the column's
                // height: at a 1.3 system text size the selected "Uppslagsverk" was wider than its
                // fifth of the bar (Plan 3 Task 7, BottomNavLabelFitTest).
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = labelMinFontSize, maxFontSize = labelMaxFontSize),
                modifier = Modifier.heightIn(max = LABEL_MAX_HEIGHT),
            )
            Spacer(Modifier.height(3.dp))
            // Reserve the dot's footprint on every tab (selected or not) so the row of
            // labels stays vertically aligned instead of jumping when selection changes.
            Box(
                modifier =
                    Modifier
                        .size(4.dp)
                        .testTag(SELECTED_DOT_TAG)
                        .let { m -> if (selected) m.clip(CircleShape).background(AccentCopper) else m },
            )
        }
    }
}

/** Mockup: an 8dp rust dot at the icon's top right, with a 2dp ring in the bar's own colour. */
@Composable
private fun NewDot(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .offset(x = 5.dp, y = (-3).dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(AccentCopper)
                .border(2.dp, PaperBottomBar, CircleShape),
    )
}

private fun NavDestination.parentChain(): Sequence<NavDestination> = generateSequence(this) { it.parent }

/** Screens opened on top of any tab that no tab owns: the bar marks no tab on them. */
private val untabbedRoutes: Set<KClass<out AppRoute>> =
    setOf(
        AppRoute.Settings::class,
        AppRoute.About::class,
        AppRoute.OnboardingReplay::class,
        AppRoute.Premium::class,
        AppRoute.DebugBenchmark::class,
        AppRoute.DebugDiagnostics::class,
    )

private fun NavDestination.ownedBy(tab: TabSpec): Boolean {
    val chain = parentChain()
    return chain.any { dest -> tab.ownedRoutes.any { dest.hasRoute(it) } }
}

private val identifyTab: TabSpec = tabs.first { it.route == AppRoute.Listen }

/** Scan, photo-ID, audio-ID and their results: always Identifiera's, never saved or restored. */
private fun NavDestination.isIdentifyFlow(): Boolean = ownedBy(identifyTab)

// getBackStackEntry is the only public way to ask whether a route is on the back stack
// (NavController.currentBackStack is @RestrictTo(LIBRARY_GROUP), and an entry has no link to the
// one below it); it answers "no" by throwing IllegalArgumentException. Called once per change of
// the current entry (BottomNavBar's remember) and once per tap, at most four routes each time.
@Suppress("SwallowedException")
private fun NavHostController.isOnStack(route: AppRoute): Boolean =
    try {
        getBackStackEntry(route)
        true
    } catch (notOnStack: IllegalArgumentException) {
        false
    }

/**
 * The tab whose own stack the user is in (release 1.3.0 Task 7b review): the tab whose first
 * screen (Mina arter, Märken, Karta, Uppslagsverk's list) is on the stack. Those are only ever
 * opened by their tab, always right above Identifiera's start screen, so at most one is there and
 * it owns everything above it: a species opened from today's bird on Mina arter belongs to Mina
 * arter. Null = Identifiera's stack: the identify flow wherever it is (it's opened on
 * Identifiera's stack, see [navigateInIdentify]) and screens opened on Identifiera without their
 * tab's first screen (a species from today's bird, the recap from its notification). Identifiera's
 * stack is never saved: a tap on Identifiera always shows its start screen.
 */
private fun NavHostController.stackTab(): TabSpec? {
    if (currentDestination?.isIdentifyFlow() == true) return null
    return tabs.firstOrNull { it != identifyTab && isOnStack(it.rootRoute) }
}

/**
 * The tab the bar marks: Identifiera on the identify flow; the tab whose stack this is; on
 * Identifiera's stack the tab owning the screen itself (a species: Uppslagsverk; the recap
 * opened from its notification: Mina arter; a find: the screen it was opened from); none on
 * Settings, About, Premium, the intro and the debug screens.
 */
private fun NavHostController.markedTab(current: NavDestination): TabSpec? {
    val isFind = current.hasRoute(AppRoute.ObservationDetail::class)
    val shown = if (isFind) previousBackStackEntry?.destination else current
    return when {
        untabbedRoutes.any { current.hasRoute(it) } -> null
        current.isIdentifyFlow() -> identifyTab
        else -> stackTab() ?: tabs.firstOrNull { tab -> shown?.ownedBy(tab) == true }
    }
}

/**
 * A tap on [tab] (release 1.3.0 Task 7b review):
 * - Identifiera: back to its start screen. The stack being left is kept when it is another
 *   tab's (that tab brings it back), and dropped when it is Identifiera's own (a camera, a
 *   result, a species from today's bird): Identifiera never restores, so a result already saved
 *   can't come back to be saved again;
 * - the tab whose stack this is: back to its first screen (Mina arter on a species opened from
 *   Mina arter returns to the list); nothing on that screen itself;
 * - another tab: the usual tab switch, which saves the stack being left if it is a tab's and
 *   brings back the tapped tab's saved stack.
 */
private fun NavHostController.onTabClick(tab: TabSpec) {
    val owner = stackTab()
    when {
        tab == identifyTab -> popBackStack(AppRoute.Listen, inclusive = false, saveState = owner != null)
        owner == tab -> {
            val onRoot = currentDestination?.hasRoute(tab.rootRoute::class) == true
            if (!onRoot) popBackStack(tab.rootRoute, inclusive = false)
        }
        else ->
            navigate(tab.route) {
                popUpTo(AppRoute.Listen) { saveState = owner != null }
                launchSingleTop = true
                restoreState = true
            }
    }
}

/**
 * Identifiera's start screen from anywhere else in the app (the map's "Identifiera"), as its tab
 * does it: see [onTabClick].
 */
internal fun NavHostController.goToIdentify() {
    popBackStack(AppRoute.Listen, inclusive = false, saveState = stackTab() != null)
}

/**
 * Opens [route] of the identify flow (the recap's camera, birdy://audio) on Identifiera's stack,
 * wherever the user is (release 1.3.0 Task 7b review). Opened on top of Mina arter it used to
 * become Mina arter's: Identifiera then saved it there and Mina arter brought the camera, or a
 * result that could be saved a second time, back later. The stack being left is kept if it is a
 * tab's, as when switching tab. Nothing happens if [route] is already open.
 */
internal fun NavHostController.navigateInIdentify(route: AppRoute) {
    if (currentDestination?.hasRoute(route::class) == true) return
    val owner = stackTab()
    navigate(route) {
        popUpTo(AppRoute.Listen) { saveState = owner != null }
        launchSingleTop = true
    }
}
