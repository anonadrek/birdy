package se.birdy.app.ui.audio

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual interface AudioPermissionController {
    actual val state: StateFlow<PermissionState>

    actual fun request()

    actual fun openSettings()

    actual fun recheck()
}

/**
 * Android implementation of [AudioPermissionController].
 *
 * Unlike the previous design this class does NOT call
 * `registerForActivityResult` in its constructor — that must happen before
 * the Activity reaches STARTED. Instead the host composable uses
 * `rememberLauncherForActivityResult` (correct timing) and passes a
 * [launchRequest] lambda here. The host is also responsible for lifecycle
 * observation via `DisposableEffect` so there is no lifecycle-observer leak.
 */
class AndroidAudioPermissionController(
    private val activity: Activity,
    private val launchRequest: () -> Unit,
) : AudioPermissionController {
    private val _state = MutableStateFlow(PermissionState.Unknown)
    override val state: StateFlow<PermissionState> = _state

    init {
        recheck()
    }

    override fun request() = launchRequest()

    override fun openSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
            }
        activity.startActivity(intent)
    }

    override fun recheck() {
        val granted =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        _state.value =
            when {
                granted -> PermissionState.Granted
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO,
                ) -> PermissionState.Denied
                _state.value == PermissionState.Unknown -> PermissionState.Unknown
                else -> PermissionState.PermanentlyDenied
            }
    }

    /** Called by the host after the `ActivityResult` callback fires. */
    fun onResult(granted: Boolean) {
        _state.value =
            when {
                granted -> PermissionState.Granted
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO,
                ) -> PermissionState.Denied
                else -> PermissionState.PermanentlyDenied
            }
    }
}
