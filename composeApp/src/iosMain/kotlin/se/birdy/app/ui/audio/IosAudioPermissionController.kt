package se.birdy.app.ui.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermission
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS-actual-implementation av [AudioPermissionController] via AVAudioSession
 * (INTE AVAudioApplication — den är iOS 17+, deployment target är 16.0).
 * Spegel av [se.birdy.app.permissions.rememberIosCameraPermissionState]s mönster:
 * request → systemdialog (endast första gången), därefter är denied permanent
 * på iOS → [PermissionState.PermanentlyDenied] + openSettings. recheck() anropas
 * av hosten på UIApplicationDidBecomeActive (fångar toggle i Inställningar).
 */
class IosAudioPermissionController : AudioPermissionController {
    private val _state = MutableStateFlow(currentState())
    override val state: StateFlow<PermissionState> = _state.asStateFlow()

    override fun request() {
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            dispatch_async(dispatch_get_main_queue()) {
                _state.value = if (granted) PermissionState.Granted else PermissionState.PermanentlyDenied
            }
        }
    }

    override fun openSettings() {
        val url = NSURL(string = UIApplicationOpenSettingsURLString)
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    override fun recheck() {
        _state.value = currentState()
    }

    private fun currentState(): PermissionState = mapRecordPermission(AVAudioSession.sharedInstance().recordPermission)
}

internal fun mapRecordPermission(raw: AVAudioSessionRecordPermission): PermissionState =
    when (raw) {
        AVAudioSessionRecordPermissionGranted -> PermissionState.Granted
        AVAudioSessionRecordPermissionDenied -> PermissionState.PermanentlyDenied
        else -> PermissionState.Unknown
    }
