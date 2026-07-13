@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package se.birdy.app.ui.photoanalyze

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation
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
 * pixelmått: crop-skärmens [ImageBitmap] deriveras från exakt dessa bytes (via Skia i host:en)
 * och [finalizeCrop] avkodar samma bytes → display och crop delar ETT koordinatsystem
 * (arbetsbildens pixlar), precis som Android-hostens `Bitmap`.
 */
internal class WorkingImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

/**
 * Avkodar galleri-bytes → EXIF-orienteringen bakas in → långsidan cappas till
 * [CROP_WORKING_MAX_PX] (OOM-skydd). Returnerar null om bytes inte är en bild.
 */
internal fun decodeForCrop(bytes: ByteArray): WorkingImage? {
    val image = UIImage(data = bytes.toNSData()) ?: return null
    return image.bakeUpright(maxLongSide = CROP_WORKING_MAX_PX)
}

/**
 * Roterar arbetsbilden 90° medurs (matchar Android `Matrix.postRotate(90f)`). Ny instans →
 * host:ens `remember(working)` re-key:ar → ny [ImageBitmap] → crop-rektangeln nollställs.
 */
internal fun rotate90(working: WorkingImage): WorkingImage {
    val source = UIImage(data = working.bytes.toNSData()) ?: return working
    val cg = source.CGImage ?: return working
    // orientation .right = "rotera datan 90° medurs för visning"; bak:a den till upprätta pixlar.
    val rotated = UIImage.imageWithCGImage(cg, scale = 1.0, orientation = UIImageOrientation.UIImageOrientationRight)
    return rotated.bakeUpright(maxLongSide = CROP_WORKING_MAX_PX) ?: working
}

/**
 * Beskär arbetsbilden enligt [rect] (käll-pixel-koordinater), skalar långsidan till
 * [ANALYZE_LONG_SIDE_PX] och encodar JPEG [JPEG_QUALITY]. Crop sker i CGImage:ns pixelrymd —
 * samma rymd som [decodeForCrop]/host:ens ImageBitmap eftersom arbetsbytesen är upprätta.
 */
internal fun finalizeCrop(
    working: WorkingImage,
    rect: CropRect,
): ImageInput {
    val source = UIImage(data = working.bytes.toNSData()) ?: return emptyInput()
    val cg = source.CGImage ?: return emptyInput()
    val croppedCg =
        CGImageCreateWithImageInRect(
            cg,
            CGRectMake(
                x = rect.left.toDouble(),
                y = rect.top.toDouble(),
                width = rect.width.toDouble(),
                height = rect.height.toDouble(),
            ),
        )
    try {
        val cropped = if (croppedCg != null) UIImage.imageWithCGImage(croppedCg) else source
        val (finalW, finalH) = scaleToLongSide(rect.width, rect.height, ANALYZE_LONG_SIDE_PX)
        val jpeg = drawAndEncodeJpeg(cropped, finalW, finalH) ?: return emptyInput()
        return ImageInput(bytes = jpeg, widthPx = finalW, heightPx = finalH, format = FrameFormat.JPEG)
    } finally {
        if (croppedCg != null) CGImageRelease(croppedCg)
    }
}

/** Skriver [bytes] till `NSCachesDirectory/photo-input/<uuid>.jpg`, returnerar filens path. */
internal fun persistToCaches(bytes: ByteArray): String {
    val dir = cachesPhotoInputDir()
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val path = "$dir/${NSUUID().UUIDString}.jpg"
    bytes.toNSData().writeToFile(path, atomically = true)
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
 * Skia (host:ens ImageBitmap) och [finalizeCrop] avkodar identiska pixelmått.
 */
private fun UIImage.bakeUpright(maxLongSide: Int): WorkingImage? {
    val (width, height) = size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val longSide = maxOf(width, height)
    val scale = if (longSide > maxLongSide.toDouble()) maxLongSide.toDouble() / longSide else 1.0
    val targetW = maxOf(1, (width * scale).roundToInt())
    val targetH = maxOf(1, (height * scale).roundToInt())
    val jpeg = drawAndEncodeJpeg(this, targetW, targetH) ?: return null
    return WorkingImage(jpeg, targetW, targetH)
}

/** Ritar [image] i en opak `targetW × targetH` bitmap-kontext och encodar JPEG [JPEG_QUALITY]. */
private fun drawAndEncodeJpeg(
    image: UIImage,
    targetW: Int,
    targetH: Int,
): ByteArray? {
    UIGraphicsBeginImageContextWithOptions(
        size = CGSizeMake(targetW.toDouble(), targetH.toDouble()),
        opaque = true,
        scale = 1.0,
    )
    try {
        image.drawInRect(CGRectMake(0.0, 0.0, targetW.toDouble(), targetH.toDouble()))
        val baked = UIGraphicsGetImageFromCurrentImageContext() ?: return null
        return UIImageJPEGRepresentation(baked, JPEG_QUALITY)?.toByteArray()
    } finally {
        UIGraphicsEndImageContext()
    }
}

private fun scaleToLongSide(
    w: Int,
    h: Int,
    target: Int,
): Pair<Int, Int> {
    val longSide = maxOf(w, h)
    if (longSide <= target) return w to h
    val ratio = target.toDouble() / longSide
    return maxOf(1, (w * ratio).roundToInt()) to maxOf(1, (h * ratio).roundToInt())
}

/** Tom (0×0) input → PhotoAnalyzeViewModel.analyze rapporterar TooSmall (graceful, ingen krasch). */
private fun emptyInput(): ImageInput = ImageInput(bytes = ByteArray(0), widthPx = 0, heightPx = 0, format = FrameFormat.JPEG)

// PHPicker-delegaten hålls med stark referens här: PHPickerViewController.delegate är weak, så
// en lokal K/N-delegat skulle deallokeras direkt → callbacken avfyras aldrig. Refereras/tas bort
// bara på main-tråden (present + finish-hoppet nedan).
private val retainedPickerDelegates = mutableSetOf<NSObject>()

/**
 * Presenterar en behörighetsfri [PHPickerViewController] (endast bilder, 1 val) från key
 * window:ens root-VC. [onBytes] anropas på main-tråden med de valda bild-bytesen, eller null
 * om användaren avbröt / något gick fel.
 */
internal fun presentPhotoPicker(onBytes: (ByteArray?) -> Unit) {
    val config = PHPickerConfiguration()
    config.filter = PHPickerFilter.imagesFilter()
    config.selectionLimit = 1L
    val picker = PHPickerViewController(configuration = config)
    val delegate = GalleryPickerDelegate(onBytes)
    retainedPickerDelegates.add(delegate)
    picker.delegate = delegate

    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
    if (root == null) {
        retainedPickerDelegates.remove(delegate)
        onBytes(null)
        return
    }
    root.presentViewController(picker, animated = true, completion = null)
}

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
