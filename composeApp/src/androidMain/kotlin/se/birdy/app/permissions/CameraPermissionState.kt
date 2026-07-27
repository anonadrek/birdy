package se.birdy.app.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class CameraPermissionState(
    private val statusState: MutableState<CameraPermissionStatus>,
    private val request: () -> Unit,
    private val openSettings: () -> Unit,
) {
    val status: CameraPermissionStatus get() = statusState.value

    fun launchRequest() = request()

    fun openAppSettings() = openSettings()
}

@Composable
fun rememberCameraPermissionState(context: Context): CameraPermissionState {
    val statusState = remember { mutableStateOf(computeStatus(context)) }
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            statusState.value =
                if (granted) CameraPermissionStatus.Granted else CameraPermissionStatus.Denied
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    statusState.value = computeStatus(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(launcher) {
        CameraPermissionState(
            statusState = statusState,
            request = { launcher.launch(Manifest.permission.CAMERA) },
            openSettings = {
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = ("package:" + context.packageName).toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
            },
        )
    }
}

private fun computeStatus(context: Context): CameraPermissionStatus {
    if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return CameraPermissionStatus.Granted
    }
    val activity = context as? Activity
    return if (
        activity != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    ) {
        CameraPermissionStatus.Denied
    } else {
        CameraPermissionStatus.NotAsked
    }
}
