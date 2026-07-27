package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import se.birdy.app.di.AppGraph
import se.birdy.app.permissions.CameraPermissionStatus
import se.birdy.app.permissions.rememberIosCameraPermissionState

@Composable
actual fun ScanScreenHost(
    graph: AppGraph,
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val permission = rememberIosCameraPermissionState()
    val viewModel = viewModel { graph.scanViewModel() }
    val cameraSource = viewModel.cameraSource

    LaunchedEffect(permission.status) {
        when (permission.status) {
            CameraPermissionStatus.Granted -> viewModel.onPermissionResult(granted = true)
            CameraPermissionStatus.Denied -> viewModel.onPermissionResult(granted = false)
            CameraPermissionStatus.NotAsked -> Unit
        }
    }

    ScanScreen(
        viewModel = viewModel,
        cameraSource = cameraSource,
        onPhotoAnalyzeClick = onPhotoAnalyzeClick,
        onFrozen = onFrozen,
        onBack = onBack,
        onPermissionRequest = { permission.launchRequest() },
        onOpenSettings = { permission.openAppSettings() },
        persistFrame = { input -> persistScanFrame(input) },
    )
}
