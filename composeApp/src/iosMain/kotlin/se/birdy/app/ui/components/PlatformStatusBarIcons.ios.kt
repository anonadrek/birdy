package se.birdy.app.ui.components

import androidx.compose.runtime.Composable

/** iOS keeps UIKit's default status bar style until i6 (see the expect declaration). */
@Composable
actual fun PlatformStatusBarIcons(lightIcons: Boolean) = Unit
