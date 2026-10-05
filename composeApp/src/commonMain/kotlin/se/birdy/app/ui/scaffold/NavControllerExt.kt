package se.birdy.app.ui.scaffold

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * Pops [entry] only while it is still the top of the back stack (release 1.3.0 Plan 3 Task 2).
 * A second tap during the NavHost's exit fade, a system back racing an on-screen button, or a
 * save flow calling onBack while the user presses back, then does nothing instead of popping the
 * screen underneath. Next to the start destination a plain second popBackStack() empties the
 * NavHost (blank screen). Same idea as T11b's `popBackStack(AppRoute.Premium, inclusive = true)`,
 * but it also works for routes with arguments.
 */
internal fun NavController.popIfTop(entry: NavBackStackEntry) = currentBackStackEntry === entry && popBackStack()
