package se.birdy.app.ui.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import se.birdy.app.di.AppGraph
import se.birdy.app.permissions.CameraPermissionStatus
import se.birdy.app.permissions.rememberCameraPermissionState
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import android.graphics.Color as AndroidColor

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
            val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(AndroidColor.BLACK)
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            baos.toByteArray()
        },
        persistFrame = { bytes ->
            val file = File(cacheDir, UUID.randomUUID().toString() + ".jpg")
            file.outputStream().use { it.write(bytes) }
            file.absolutePath
        },
    )
}
