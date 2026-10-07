package se.birdy.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What currently sits under the status bar on screens that draw behind it (release 1.3.0 Plan 3
 * Task 6). Each reporter (a PhotoHero, a dark screen) has its own key, so two heroes during a
 * navigation transition can't overwrite each other, and a reporter that leaves composition is
 * removed. AppScaffold turns [anyDark] into light status bar icons.
 */
@Stable
class StatusBarBackdrop {
    private val reports = mutableStateMapOf<Any, Boolean>()

    val anyDark: Boolean
        get() = reports.values.any { it }

    fun report(
        key: Any,
        isDark: Boolean,
    ) {
        reports[key] = isDark
    }

    fun remove(key: Any) {
        reports.remove(key)
    }
}

val LocalStatusBarBackdrop = staticCompositionLocalOf<StatusBarBackdrop?> { null }

/** Reports whether this caller's content under the status bar is dark. No-op outside AppScaffold. */
@Composable
fun ReportStatusBarBackdrop(isDark: Boolean) {
    val backdrop = LocalStatusBarBackdrop.current ?: return
    val key = remember { Any() }
    SideEffect { backdrop.report(key, isDark) }
    DisposableEffect(backdrop, key) { onDispose { backdrop.remove(key) } }
}
