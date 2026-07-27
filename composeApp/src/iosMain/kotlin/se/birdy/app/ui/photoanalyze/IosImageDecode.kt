@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package se.birdy.app.ui.photoanalyze

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGInterpolationMedium
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import se.birdy.app.toByteArray
import se.birdy.app.toNSData
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import kotlin.math.roundToInt

// Speglar Android-hostens konstanter (PhotoAnalyzeHost.android.kt).
private const val CROP_WORKING_MAX_PX = 2048
private const val ANALYZE_LONG_SIDE_PX = 1024
private const val JPEG_QUALITY = 0.9
private const val IMAGE_UTI = "public.image"

/**
 * Orienterings-bakad, storleks-cappad arbetsbild. Representeras som upprätt JPEG-bytes plus
 * pixelmått: [imageBitmap] är avkodad från exakt dessa bytes ([finalizeCrop] avkodar samma
 * bytes för croppningen) → display och crop delar ETT koordinatsystem (arbetsbildens pixlar),
 * precis som Android-hostens `Bitmap`. [imageBitmap] avkodas redan vid konstruktion (i
 * [bakeUpright], som alltid körs inuti host:ens `withContext(Dispatchers.Default)`-block för
 * både bygg och rotera) — komposition läser bara ett redan avkodat värde, aldrig en synkron
 * Skia-avkodning.
 */
internal class WorkingImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val imageBitmap: ImageBitmap,
)

/**
 * Avkodar galleri-bytes → EXIF-orienteringen bakas in → långsidan cappas till
 * [CROP_WORKING_MAX_PX] (OOM-skydd). Returnerar null om bytes inte är en bild, inklusive
 * en tom (0-byte) pick — en tom [ByteArray] kan inte pinnas säkert (jfr
 * `NSData.toByteArray()` i SpeciesRepositoryProvider.ios.kt).
 */
internal fun decodeForCrop(bytes: ByteArray): WorkingImage? {
    if (bytes.isEmpty()) return null
    val image = uiImageFromDataOrNull(bytes.toNSData()) ?: return null
    return image.bakeUpright(maxLongSide = CROP_WORKING_MAX_PX)
}

/**
 * Roterar arbetsbilden 90° medurs (matchar Android `Matrix.postRotate(90f)`). Ny instans →
 * host:ens `remember(working)` re-key:ar → ny [ImageBitmap] → crop-rektangeln nollställs.
 */
internal fun rotate90(working: WorkingImage): WorkingImage {
    val source = uiImageFromDataOrNull(working.bytes.toNSData()) ?: return working
    val cg = source.CGImage ?: return working
    // orientation .right = "rotera datan 90° medurs för visning"; bak:a den till upprätta pixlar.
    val rotated = UIImage.imageWithCGImage(cg, scale = 1.0, orientation = UIImageOrientation.UIImageOrientationRight)
    return rotated.bakeUpright(maxLongSide = CROP_WORKING_MAX_PX) ?: working
}

/**
 * Beskär arbetsbilden enligt [rect] (käll-pixel-koordinater), skalar långsidan till
 * [ANALYZE_LONG_SIDE_PX] och encodar JPEG [JPEG_QUALITY]. Crop sker i CGImage:ns pixelrymd —
 * samma rymd som [decodeForCrop]/host:ens ImageBitmap eftersom arbetsbytesen är upprätta.
 *
 * Returnerar null på varje avkodnings-/beskärnings-/encode-fel (aldrig en tyst felaktig
 * bild) — callern ska tolka det som ett analys-fel, inte som en lyckad crop.
 */
internal fun finalizeCrop(
    working: WorkingImage,
    rect: CropRect,
): ImageInput? {
    val source = uiImageFromDataOrNull(working.bytes.toNSData()) ?: return null
    val cg = source.CGImage ?: return null
    val croppedCg =
        CGImageCreateWithImageInRect(
            cg,
            CGRectMake(
                x = rect.left.toDouble(),
                y = rect.top.toDouble(),
                width = rect.width.toDouble(),
                height = rect.height.toDouble(),
            ),
        ) ?: return null
    try {
        val cropped = UIImage.imageWithCGImage(croppedCg)
        // CGImageCreateWithImageInRect integraliserar och snittar rect mot bildens gränser,
        // så den faktiska croppade storleken kan avvika från rect — mät croppedCg, inte rect
        // (Android läser motsvarande cropped.width/height, PhotoAnalyzeHost.android.kt:227).
        val croppedW = CGImageGetWidth(croppedCg).toInt()
        val croppedH = CGImageGetHeight(croppedCg).toInt()
        val (finalW, finalH) = scaleToLongSide(croppedW, croppedH, ANALYZE_LONG_SIDE_PX)
        val jpeg = drawAndEncodeJpeg(cropped, finalW, finalH) ?: return null
        return ImageInput(bytes = jpeg, widthPx = finalW, heightPx = finalH, format = FrameFormat.JPEG)
    } finally {
        CGImageRelease(croppedCg)
    }
}

/**
 * Skriver [bytes] till `NSCachesDirectory/photo-input/<uuid>.jpg`, returnerar filens path.
 * Kastar om katalogen inte kunde skapas eller skrivningen misslyckades — en tystad fel-
 * signal här skulle låta [PhotoAnalyzeViewModel] peka `Loaded.frameJpegPath` på en fil som
 * aldrig skrevs (Android-motsvarigheten, `file.outputStream().use { it.write(bytes) }`,
 * kastar på samma sätt; se PhotoAnalyzeHost.android.kt:55).
 */
internal fun persistToCaches(bytes: ByteArray): String {
    val dir = cachesPhotoInputDir()
    memScoped {
        val errorVar = alloc<ObjCObjectVar<NSError?>>()
        val created =
            NSFileManager.defaultManager.createDirectoryAtPath(
                path = dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = errorVar.ptr,
            )
        if (!created) {
            error("photo persist: failed to create directory $dir: ${errorVar.value?.localizedDescription}")
        }
    }
    val path = "$dir/${NSUUID().UUIDString}.jpg"
    if (!bytes.toNSData().writeToFile(path, atomically = true)) {
        error("photo persist: failed to write $path")
    }
    return path
}

private fun cachesPhotoInputDir(): String {
    val caches =
        NSFileManager.defaultManager
            .URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
            .firstOrNull() as? NSURL
    val base = caches?.path ?: NSTemporaryDirectory()
    return "$base/photo-input"
}

/**
 * Ritar [image] (orienterings-respekterat) i en upprätt kontext, cappar långsidan till
 * [maxLongSide] och encodar JPEG. Resultatet saknar EXIF-orientering (default "up") så både
 * Skia (host:ens ImageBitmap) och [finalizeCrop] avkodar identiska pixelmått. Avkodar även
 * [WorkingImage.imageBitmap] här — dvs på samma tråd som anroparen kör detta på (host:ens
 * withContext(Dispatchers.Default)-block), inte i composition.
 */
private fun UIImage.bakeUpright(maxLongSide: Int): WorkingImage? {
    val (width, height) = size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val longSide = maxOf(width, height)
    val scale = if (longSide > maxLongSide.toDouble()) maxLongSide.toDouble() / longSide else 1.0
    val targetW = maxOf(1, (width * scale).roundToInt())
    val targetH = maxOf(1, (height * scale).roundToInt())
    val jpeg = drawAndEncodeJpeg(this, targetW, targetH) ?: return null
    val imageBitmap = jpeg.decodeToImageBitmapOrNull() ?: return null
    return WorkingImage(jpeg, targetW, targetH, imageBitmap)
}

/**
 * Avkodar JPEG-bytes → Compose [ImageBitmap]. `Image.makeFromEncoded` kastar på odekodningsbara
 * bytes; fångas här så ett dåligt bygge ger en null [WorkingImage] (→ callern tolkar det som
 * ett avkodningsfel, t.ex. `viewModel.decodeFailed()`) istället för ett kast inifrån komposition.
 */
private fun ByteArray.decodeToImageBitmapOrNull(): ImageBitmap? =
    runCatching { Image.makeFromEncoded(this).toComposeImageBitmap() }.getOrNull()

/** Ritar [image] i en opak `targetW × targetH` bitmap-kontext och encodar JPEG med [quality]. */
internal fun drawAndEncodeJpeg(
    image: UIImage,
    targetW: Int,
    targetH: Int,
    quality: Double = JPEG_QUALITY,
): ByteArray? {
    UIGraphicsBeginImageContextWithOptions(
        size = CGSizeMake(targetW.toDouble(), targetH.toDouble()),
        opaque = true,
        scale = 1.0,
    )
    try {
        // Medium ≈ bilinear, matchar ImagePreprocessor.ios.kt:s val och Android
        // createScaledBitmap(filter=true) — utan detta faller UIGraphics context tillbaka på
        // kCGInterpolationDefault och driver cross-platform-resultaten isär.
        CGContextSetInterpolationQuality(UIGraphicsGetCurrentContext(), kCGInterpolationMedium)
        image.drawInRect(CGRectMake(0.0, 0.0, targetW.toDouble(), targetH.toDouble()))
        val baked = UIGraphicsGetImageFromCurrentImageContext() ?: return null
        return UIImageJPEGRepresentation(baked, quality)?.toByteArray()
    } finally {
        UIGraphicsEndImageContext()
    }
}

internal fun scaleToLongSide(
    w: Int,
    h: Int,
    target: Int,
): Pair<Int, Int> {
    val longSide = maxOf(w, h)
    if (longSide <= target) return w to h
    val ratio = target.toDouble() / longSide
    return maxOf(1, (w * ratio).roundToInt()) to maxOf(1, (h * ratio).roundToInt())
}

/** Läser pixelmåtten ur en JPEG-fil på disk (test + storage-verifiering). */
internal fun readJpegPixelSize(path: String): Pair<Int, Int>? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val image = uiImageFromDataOrNull(data) ?: return null
    val (w, h) = image.size.useContents { width to height }
    if (w <= 0.0 || h <= 0.0) return null
    return w.roundToInt() to h.roundToInt()
}

/**
 * K/N-brygga för UIImages failable init: konstruktorn kan inte returnera null i Kotlin,
 * så cinterop kastar rå NullPointerException när ObjC-init:en ger nil (verifierat i i2c
 * T7 med probe-test). Utan denna guard är `UIImage(data=...) ?: ...` DÖD elvis — korrupta
 * bytes kraschar istället för att ge decodeFailed-flödet.
 */
internal fun uiImageFromDataOrNull(data: NSData): UIImage? =
    try {
        UIImage(data = data)
    } catch (_: NullPointerException) {
        null
    }

// PHPicker-delegaten hålls med stark referens här: PHPickerViewController.delegate är weak, så
// en lokal K/N-delegat skulle deallokeras direkt → callbacken avfyras aldrig. Refereras/tas bort
// bara på main-tråden (present + finish-hoppet nedan).
internal val retainedPickerDelegates = mutableSetOf<NSObject>()

/**
 * Presenterar en behörighetsfri [PHPickerViewController] (endast bilder, 1 val) från key
 * window:ens root-VC. [onBytes] anropas på main-tråden med de valda bild-bytesen, eller null
 * om användaren avbröt valet. [onPresentFailure] anropas synkront (aldrig [onBytes]) om ingen
 * key window/root-VC kunde hittas — en distinkt signal från "avbröt", så callern kan visa ett
 * fel istället för att tyst göra ingenting.
 */
internal fun presentPhotoPicker(
    onBytes: (ByteArray?) -> Unit,
    onPresentFailure: () -> Unit,
) {
    val root = keyWindowRootViewController()
    if (root == null) {
        onPresentFailure()
        return
    }
    val config = PHPickerConfiguration()
    config.filter = PHPickerFilter.imagesFilter()
    config.selectionLimit = 1L
    val picker = PHPickerViewController(configuration = config)
    val delegate = GalleryPickerDelegate(onBytes)
    retainedPickerDelegates.add(delegate)
    picker.delegate = delegate
    root.presentViewController(picker, animated = true, completion = null)
}

/**
 * `UIApplication.keyWindow` är deprecated sedan iOS 13 för scen-baserade appar (denna app är
 * scen-baserad, se iosApp/iosApp/iOSApp.swift). Hitta key window via `connectedScenes` istället.
 */
internal fun keyWindowRootViewController(): UIViewController? =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { it.windows }
        .filterIsInstance<UIWindow>()
        .firstOrNull { it.isKeyWindow() }
        ?.rootViewController

private class GalleryPickerDelegate(
    private val onBytes: (ByteArray?) -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            finish(null)
            return
        }
        // loadDataRepresentation ger rå fil-bytes (EXIF bevarat); completion kan ligga på annan kö.
        result.itemProvider.loadDataRepresentationForTypeIdentifier(IMAGE_UTI) { data, _ ->
            finish(data?.toByteArray())
        }
    }

    private fun finish(bytes: ByteArray?) {
        dispatch_async(dispatch_get_main_queue()) {
            retainedPickerDelegates.remove(this)
            onBytes(bytes)
        }
    }
}
