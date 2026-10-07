package se.birdy.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Light navigation bar icons over a dark screen bottom, dark icons over paper (release 1.3.0
 * Task 7g). With three-button navigation (API 30 and older phones) the buttons sit on whatever the
 * screen draws at its bottom edge: the paper bottom bar on most screens, dark moss on Premium.
 * Android sets the window's navigation bar appearance; iOS has no navigation bar.
 */
@Composable
expect fun PlatformNavigationBarIcons(lightIcons: Boolean)
