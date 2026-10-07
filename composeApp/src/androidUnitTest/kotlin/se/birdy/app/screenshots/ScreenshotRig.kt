package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme

/**
 * Renders a commonMain screen inside BirdyTheme and saves a PNG under
 * composeApp/build/outputs/roborazzi/<name>.png. The language comes from the test's
 * @Config(qualifiers = "+sv" | "+en") — values/ is Swedish, values-en/ is English.
 * Only runs with -Pbirdy.screenshots=true (see composeApp/build.gradle.kts).
 *
 * [settle], when given, runs once right after the initial [setContent] and before the final
 * [waitForIdle] — a hook for screens whose ViewModel needs real (virtual) time to pass before
 * its state settles, e.g. a search-query `debounce()`. Robolectric's main Looper is PAUSED by
 * default, so a coroutine `delay()` scheduled on `Dispatchers.Main` never fires on its own; the
 * caller advances it explicitly (see `ArchiveScreenshotTest`'s `advanceMainLooper`). Defaults to
 * a no-op, so existing callers are unaffected.
 */
internal fun ComposeContentTestRule.captureScreen(
    name: String,
    settle: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    attachComposeResourcesContext()
    setContent { BirdyTheme { content() } }
    settle()
    waitForIdle()
    onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
}
