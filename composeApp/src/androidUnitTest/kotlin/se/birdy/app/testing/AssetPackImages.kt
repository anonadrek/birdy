package se.birdy.app.testing

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.BitmapFactoryDecoder
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import org.robolectric.RuntimeEnvironment
import java.io.File

private const val ASSET_PACK_IMAGES = "file:///android_asset/images/"

/**
 * Lets Robolectric screenshots show the species' real plate photos (release 1.3.0 Task 7c).
 *
 * In the app, `speciesImageUri` points Coil at `file:///android_asset/images/...` in the
 * install-time asset pack, which a JVM unit test does not have. This installs a singleton Coil
 * loader that reads the same files straight from `asset-pack/src/main/assets/images/` in the repo,
 * decodes them with BitmapFactory (Robolectric has no ImageDecoder) and runs every load on
 * [Dispatchers.Unconfined] so the image is decoded before the compose rule reports idle (Coil's
 * background dispatcher is invisible to it). Call [resetImageLoader] after
 * the test. Test-only: production code is untouched.
 */
@OptIn(DelicateCoilApi::class)
internal fun installAssetPackImageLoader() {
    val imagesDir = File(projectRoot(), "asset-pack/src/main/assets/images")
    val loader =
        ImageLoader
            .Builder(RuntimeEnvironment.getApplication())
            .components {
                add(AssetPackRedirect(imagesDir))
                // Robolectric has no native ImageDecoder ("Only supported on Android"): decode with
                // BitmapFactory, which it does implement. User decoders are tried before Coil's own.
                add(BitmapFactoryDecoder.Factory())
            }.coroutineContext(Dispatchers.Unconfined)
            .allowHardware(false)
            .build()
    SingletonImageLoader.setUnsafe(loader)
}

@OptIn(DelicateCoilApi::class)
internal fun resetImageLoader() = SingletonImageLoader.reset()

private class AssetPackRedirect(
    private val imagesDir: File,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data is String && data.startsWith(ASSET_PACK_IMAGES)) {
            val file = File(imagesDir, data.removePrefix(ASSET_PACK_IMAGES))
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
}

private fun projectRoot(): File {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        if (File(dir, "settings.gradle.kts").isFile) return dir
        dir = dir.parentFile
    }
    error("settings.gradle.kts not found from ${System.getProperty("user.dir")}")
}
