package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

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

/** Test tag on [StatusBarBand] (StatusBarBandTest). */
internal const val STATUS_BAR_BAND_TAG = "status-bar-band"

/**
 * A band in the page's [color] behind the status bar, for a screen whose [PhotoHero] draws its
 * photo behind the status bar with the name below it (textBelowPhoto: the species profile,
 * Match). Shown only once the photo has scrolled out from under the status bar
 * ([photoScrolledAway]), so the name and the text scrolling up after it don't run under the
 * clock and the icons; while any of the photo is there nothing is drawn over the bird. PhotoHero
 * switches the icons to dark at that same point. Release 1.3.0 Task 7b review.
 *
 * Draw it after (over) the scrolling content and before the back button.
 */
@Composable
fun BoxScope.StatusBarBand(
    photoScrolledAway: Boolean,
    color: Color,
) {
    if (!photoScrolledAway) return
    Box(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(color)
            .testTag(STATUS_BAR_BAND_TAG),
    )
}
