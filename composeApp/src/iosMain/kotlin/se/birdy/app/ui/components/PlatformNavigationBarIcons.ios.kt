package se.birdy.app.ui.components

import androidx.compose.runtime.Composable

/** iOS has no navigation bar buttons to colour (the home indicator adapts by itself). */
@Composable
actual fun PlatformNavigationBarIcons(lightIcons: Boolean) = Unit
