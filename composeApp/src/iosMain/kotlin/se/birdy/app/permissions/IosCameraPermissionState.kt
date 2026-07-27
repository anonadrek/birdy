package se.birdy.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS-spegel av Androids [CameraPermissionState]: AVCaptureDevice-auktorisering med
 * samma trestatus-mappning (.authorized→Granted, .denied/.restricted→Denied,
 * .notDetermined→NotAsked) och omkontroll när appen blir aktiv igen — fångar
 * "användaren flippade togglen i Inställningar", precis som Androids ON_RESUME-observer.
 */
class IosCameraPermissionState(
    private val statusState: MutableState<CameraPermissionStatus>,
    private val request: () -> Unit,
    private val openSettings: () -> Unit,
) {
    val status: CameraPermissionStatus get() = statusState.value

    fun launchRequest() = request()

    fun openAppSettings() = openSettings()
}

@Composable
fun rememberIosCameraPermissionState(): IosCameraPermissionState {
    val statusState = remember { mutableStateOf(computeStatus()) }
    DisposableEffect(Unit) {
        val observer =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> statusState.value = computeStatus() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return remember {
        IosCameraPermissionState(
            statusState = statusState,
            request = {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        statusState.value =
                            if (granted) CameraPermissionStatus.Granted else CameraPermissionStatus.Denied
                    }
                }
            },
            openSettings = {
                val url = NSURL(string = UIApplicationOpenSettingsURLString)
                UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
            },
        )
    }
}

private fun computeStatus(): CameraPermissionStatus =
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> CameraPermissionStatus.Granted
        AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> CameraPermissionStatus.Denied
        else -> CameraPermissionStatus.NotAsked
    }
