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
 */
@Suppress("UNCHECKED_CAST")
internal fun ComposeContentTestRule.captureScreen(
    name: String,
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
        Class.forName("org.jetbrains.compose.resources.AndroidContextProvider") as Class<ContentProvider>
    Robolectric.setupContentProvider(providerClass)
    setContent { BirdyTheme { content() } }
    waitForIdle()
    onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
}
