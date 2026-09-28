package se.birdy.app.screenshots

import android.content.ContentProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.Robolectric
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
 *
 * The unchecked cast: `Class.forName` returns `Class<*>` but `setupContentProvider` needs
 * `Class<ContentProvider>`, and the provider class is Kotlin-internal to compose-resources, so
 * it can only be reached by name.
 */
@Suppress("UNCHECKED_CAST")
internal fun ComposeContentTestRule.captureScreen(
    name: String,
    settle: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // compose-resources reads its Application context from an internal ContentProvider
    // (org.jetbrains.compose.resources.AndroidContextProvider — see ResourceReader.android.kt).
    // It's `internal` to the compose-resources module, so the Kotlin symbol itself isn't
    // reachable here even though it's public at the JVM bytecode level (confirmed via
    // javap) — go through Class.forName so the Kotlin compiler never sees the reference.
    // Robolectric never attaches app content providers on its own for local unit tests,
    // so stringResource()/font loading throws "Android context is not initialized"
    // unless we attach it ourselves first (robolectric/robolectric#9603).
    val providerClass =
        try {
            Class.forName("org.jetbrains.compose.resources.AndroidContextProvider") as Class<ContentProvider>
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException(
                "compose-resources' internal AndroidContextProvider class has moved or been renamed. " +
                    "captureScreen() needs it to attach an Application context under Robolectric so " +
                    "Res.string/fonts resolve — look in compose-resources' ResourceReader.android.kt " +
                    "for the new class name and update this Class.forName call.",
                e,
            )
        }
    Robolectric.setupContentProvider(providerClass)
    setContent { BirdyTheme { content() } }
    settle()
    waitForIdle()
    onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
}
