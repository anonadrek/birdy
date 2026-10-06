package se.birdy.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun PlatformNavigationBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        // isAppearanceLightNavigationBars = true means DARK icons (for a light background).
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !lightIcons
    }
}
