package se.birdy.app.ui.components

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun PlatformNavigationBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val window = (context as? Activity)?.window ?: return@SideEffect
        // isAppearanceLightNavigationBars = true means DARK icons (for a light background).
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !lightIcons
    }
}
