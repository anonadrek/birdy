package se.birdy.app.ui.audio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual interface AudioPermissionController {
    actual val state: StateFlow<PermissionState>

    actual fun request()

    actual fun openSettings()

    actual fun recheck()
}

class AndroidAudioPermissionController(
    private val activity: ComponentActivity,
) : AudioPermissionController,
    DefaultLifecycleObserver {
    private val _state = MutableStateFlow(PermissionState.Unknown)
    override val state: StateFlow<PermissionState> = _state

    private val launcher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            _state.value =
                if (granted) {
                    PermissionState.Granted
                } else {
                    if (
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.RECORD_AUDIO,
                        )
                    ) {
                        PermissionState.Denied
                    } else {
                        PermissionState.PermanentlyDenied
                    }
                }
        }

    init {
        activity.lifecycle.addObserver(this)
        recheck()
    }

    override fun onResume(owner: LifecycleOwner) {
        recheck()
    }

    override fun request() {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

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
}
