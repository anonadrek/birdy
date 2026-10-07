package se.birdy.app.screenshots

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import birdy_bird_scanner.composeapp.generated.resources.Res
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.io.File

/**
 * Lets a screenshot show the REAL bird photos inside screens that own their own `AsyncImage`
 * call (the Identify hero, the species profile, Match, Premium, Mina arter). Coil's normal
 * pipeline never finishes under Robolectric, so the other screenshot tests only ever show the
 * empty placeholder behind a photo; here Coil's preview handler (used when [LocalInspectionMode]
 * is on) answers each request synchronously by decoding the file itself:
 *
 * - `file:///android_asset/images/<QID>/<file>.webp` (speciesImageUri) → the shipped photo in
 *   `asset-pack/src/main/assets/images/`
 * - `.../files/premium/great-tit-hero.jpg` (Res.getUri) → the bundled compose resource
 * - `file:///fake/<id>.jpg` (a find's own photo in the fixtures) → one of the benchmark photos
 *   in `composeApp/src/androidDebug/assets/benchmark/`, chosen by [findPhotos]. They live in the
 *   debug source set so that they never reach the release build (1.3.0 legal review, fix F);
 *   read straight from that folder, so it works for any test variant. With [findPlates] instead,
 *   a species' plate photo from the asset pack stands in for the find's own photo (the weekly
 *   recap shows more finds than there are benchmark photos).
 *
 * The working directory of a unit test is the `composeApp` module, hence the relative paths.
 */
@OptIn(ExperimentalCoilApi::class, ExperimentalResourceApi::class)
@Composable
internal fun WithRealPhotos(
    findPhotos: Map<String, String> = emptyMap(),
    findPlates: Map<String, String> = emptyMap(),
    content: @Composable () -> Unit,
) {
    val handler =
        AsyncImagePreviewHandler { request ->
            val data = request.data.toString()
            val bytes: ByteArray? =
                when {
                    data.startsWith(SPECIES_PREFIX) ->
                        File(ASSET_PACK_IMAGES, data.removePrefix(SPECIES_PREFIX)).takeIf { it.isFile }?.readBytes()
                    data.endsWith(PREMIUM_PHOTO) -> Res.readBytes(PREMIUM_PHOTO)
                    data.startsWith(FIND_PREFIX) -> {
                        val name = data.removePrefix(FIND_PREFIX)
                        (findPhotos[name]?.let { File(BENCHMARK_PHOTOS, it) } ?: findPlates[name]?.let { File(ASSET_PACK_IMAGES, it) })
                            ?.takeIf { it.isFile }
                            ?.readBytes()
                    }
                    else -> null
                }
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImage()
        }
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides handler,
    ) {
        content()
    }
}

private const val SPECIES_PREFIX = "file:///android_asset/images/"
private const val FIND_PREFIX = "file:///fake/"
private const val PREMIUM_PHOTO = "files/premium/great-tit-hero.jpg"
private const val ASSET_PACK_IMAGES = "../asset-pack/src/main/assets/images"
private const val BENCHMARK_PHOTOS = "src/androidDebug/assets/benchmark"
