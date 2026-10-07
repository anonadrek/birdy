package se.birdy.app.testing

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Robolectric gives the window no status bar, so a hero drawn behind it never shows what sits
 * under the status-bar icons. [Capture] remembers the compose view; [apply] (call it after
 * a first idle, e.g. from `captureScreen`'s `settle`, so the root's own zero-inset pass is done)
 * hands that view a status-bar inset of `topPx`. Compose's WindowInsets listen to the view's
 * applied insets, so the hero then lays out exactly as on a phone with that status bar.
 */
internal class StatusBarInset(
    private val topPx: Int,
) {
    private var view: View? = null

    @Composable
    fun Capture() {
        view = LocalView.current
    }

    fun apply() {
        val target = checkNotNull(view) { "Capture() was never composed" }
        val insets =
            WindowInsetsCompat
                .Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, topPx, 0, 0))
                .build()
        ViewCompat.dispatchApplyWindowInsets(target, insets)
    }
}
