package se.birdy.app.testing

import android.content.ContentProvider
import org.robolectric.Robolectric

/**
 * compose-resources reads its Application context from its own ContentProvider
 * (org.jetbrains.compose.resources.AndroidContextProvider, see ResourceReader.android.kt).
 * Robolectric never attaches app content providers in local unit tests, so stringResource()
 * and fonts throw "Android context is not initialized" until this runs
 * (robolectric/robolectric#9603). Call it before setContent in every Robolectric Compose test.
 *
 * The class is `internal` to compose-resources, so it is loaded by name: the Kotlin compiler
 * never sees the reference, even though the class is public in the bytecode.
 */
@Suppress("UNCHECKED_CAST") // Class.forName gives Class<*>; the class is a ContentProvider, so the cast is safe.
internal fun attachComposeResourcesContext() {
    val providerClass =
        try {
            Class.forName("org.jetbrains.compose.resources.AndroidContextProvider") as Class<ContentProvider>
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException(
                "compose-resources' internal AndroidContextProvider class has moved or been renamed. " +
                    "Robolectric tests need it to attach an Application context so Res.string/fonts " +
                    "resolve: look in compose-resources' ResourceReader.android.kt for the new class " +
                    "name and update this Class.forName call.",
                e,
            )
        }
    Robolectric.setupContentProvider(providerClass)
}
