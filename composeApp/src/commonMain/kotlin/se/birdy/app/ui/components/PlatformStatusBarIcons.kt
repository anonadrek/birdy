package se.birdy.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Light status bar icons over a dark photo top, dark icons over paper (release 1.3.0 Plan 3
 * Task 6). Android sets the window's status bar appearance; iOS is a no-op for now (the Mac
 * sim check decides whether iOS needs its own status bar style, i6).
 */
@Composable
expect fun PlatformStatusBarIcons(lightIcons: Boolean)
