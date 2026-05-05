package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import se.birdy.app.di.AppGraph
import se.birdy.app.permissions.CameraPermissionStatus
import se.birdy.app.permissions.rememberCameraPermissionState
import se.birdy.ml.camera.AndroidCameraSource
import java.io.File
import java.util.UUID

@Composable
actual fun ScanScreenHost(
    graph: AppGraph,
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (predictionsCsv: String, frameJpegPath: String) -> Unit,
) {
    val context = LocalContext.current
    val permission = rememberCameraPermissionState(context)
    val viewModel = viewModel { graph.scanViewModel() }
    val cameraSource = remember(graph) { graph.cameraSourceFactory() }

    LaunchedEffect(permission.status) {
        when (permission.status) {
            CameraPermissionStatus.Granted -> viewModel.onPermissionResult(granted = true)
            CameraPermissionStatus.Denied -> viewModel.onPermissionResult(granted = false)
            CameraPermissionStatus.NotAsked -> Unit
        }
    }

    val cacheDir = remember(context) { File(context.cacheDir, "scan-frames").apply { mkdirs() } }

    ScanScreen(
        viewModel = viewModel,
        cameraSource = cameraSource,
        onPhotoAnalyzeClick = onPhotoAnalyzeClick,
        onFrozen = onFrozen,
        onPermissionRequest = { permission.launchRequest() },
        onOpenSettings = { permission.openAppSettings() },
        onCaptureJpeg = {
            val androidSource = cameraSource as? AndroidCameraSource
            androidSource?.lastJpegBytes() ?: byteArrayOf()
        },
        persistFrame = { bytes ->
            val file = File(cacheDir, UUID.randomUUID().toString() + ".jpg")
            file.outputStream().use { it.write(bytes) }
            file.absolutePath
        },
    )
}
