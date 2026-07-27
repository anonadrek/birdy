package se.birdy.app.ui.photoanalyze

import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import se.birdy.app.toByteArray

// Samma kvalitet som crop-vägens JPEG_QUALITY (IosImageDecode.kt) — bytesen går in i
// exakt samma decodeForCrop-pipeline som galleri-picken.
private const val CAPTURE_JPEG_QUALITY = 0.9

/** False i simulatorn (ingen kamera) → ta-foto-knappen är en tyst no-op där (spec §5). */
internal fun isCameraCaptureAvailable(): Boolean =
    UIImagePickerController.isSourceTypeAvailable(
        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
    )

/**
 * Presenterar systemkameran (UIImagePickerController, iOS-motsvarigheten till Androids
 * TakePicture-intent). [onBytes] får JPEG-bytes (EXIF-orientering bevarad — decodeForCrop:s
 * bakeUpright hanterar den) eller null vid avbrutet val; [onPresentFailure] anropas synkront
 * om ingen root-VC fanns. Delegaten strong-retainas i [retainedPickerDelegates] —
 * `.delegate` är weak (i2b-trapen).
 */
internal fun presentCameraCapture(
    onBytes: (ByteArray?) -> Unit,
    onPresentFailure: () -> Unit,
) {
    val root = keyWindowRootViewController()
    if (root == null) {
        onPresentFailure()
        return
    }
    val picker = UIImagePickerController()
    picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    val delegate = CameraCaptureDelegate(onBytes)
    retainedPickerDelegates.add(delegate)
    picker.delegate = delegate
    root.presentViewController(picker, animated = true, completion = null)
}

private class CameraCaptureDelegate(
    private val onBytes: (ByteArray?) -> Unit,
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        finish(image?.let { UIImageJPEGRepresentation(it, CAPTURE_JPEG_QUALITY)?.toByteArray() })
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        finish(null)
    }

    private fun finish(bytes: ByteArray?) {
        dispatch_async(dispatch_get_main_queue()) {
            retainedPickerDelegates.remove(this)
            onBytes(bytes)
        }
    }
}
