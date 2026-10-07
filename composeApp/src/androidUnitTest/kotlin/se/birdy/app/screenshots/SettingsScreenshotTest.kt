package se.birdy.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.BitmapFactoryDecoder
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.testing.resetImageLoader
import se.birdy.app.ui.settings.SettingsScreen
import se.birdy.app.ui.settings.SettingsViewModel
import se.birdy.domain.premium.PremiumState
import java.io.File

/**
 * Settings for a free user (release 1.3.0 Task 7g items 4 and 5): the Premium card's text on its
 * moss scrim with the apricot accent, and the PLATS section lined up with the others.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() = resetImageLoader()

    private fun capture(name: String) {
        installComposeResourcesImageLoader()
        compose.captureScreen(name) {
            SettingsScreen(
                viewModel =
                    SettingsViewModel(
                        prefs = FakeUserPreferences(),
                        premiumRepository = FakePremiumRepository(PremiumState.Free),
                    ),
                onBack = {},
                onPremiumClick = {},
                onNavigateToAbout = {},
                onShowIntroAgain = {},
                versionName = "1.3.0",
            )
        }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun settings_free_sv() = capture("settings_free_sv")

    @Test
    @Config(qualifiers = "+en")
    fun settings_free_en() = capture("settings_free_en")
}

/**
 * The card loads its photo from `Res.getUri("files/premium/...")`, an android_asset URI that a JVM
 * test does not have: read the same file from the repo instead (as AssetPackImages does for plates).
 */
@OptIn(DelicateCoilApi::class)
private fun installComposeResourcesImageLoader() {
    val loader =
        ImageLoader
            .Builder(RuntimeEnvironment.getApplication())
            .components {
                add(ComposeResourcesRedirect())
                add(BitmapFactoryDecoder.Factory())
            }.coroutineContext(Dispatchers.Unconfined)
            .allowHardware(false)
            .build()
    SingletonImageLoader.setUnsafe(loader)
}

private class ComposeResourcesRedirect : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        val marker = "/files/"
        if (data is String && data.startsWith("file:///android_asset/composeResources/") && marker in data) {
            val file = File(resourcesDir(), "files/" + data.substringAfter(marker))
            return chain
                .withRequest(
                    chain.request
                        .newBuilder()
                        .data(file)
                        .build(),
                ).proceed()
        }
        return chain.proceed()
    }

    private fun resourcesDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "composeApp/src/commonMain/composeResources")
            if (candidate.isDirectory) return candidate
            val local = File(dir, "src/commonMain/composeResources")
            if (local.isDirectory) return local
            dir = dir.parentFile
        }
        error("composeResources not found from ${System.getProperty("user.dir")}")
    }
}
