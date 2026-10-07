package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import se.birdy.app.di.AppGraph
import se.birdy.app.permissions.CameraPermissionStatus
import se.birdy.app.permissions.rememberIosCameraPermissionState
import se.birdy.app.ui.components.PhotoModelStartingView
import se.birdy.app.ui.components.PhotoModelUnavailableView
import se.birdy.ml.ClassifierBootstrapState

@Composable
actual fun ScanScreenHost(
    graph: AppGraph,
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val bootstrapState by graph.classifierBootstrap.state.collectAsState()
    if (bootstrapState is ClassifierBootstrapState.Failed) {
        // The photo model failed to load — AppGraph.scanViewModel() would throw reading
        // AppGraph.classifier. Show the shared error state instead of constructing the VM.
        PhotoModelUnavailableView(onRetry = { graph.classifierBootstrap.retry() }, onBack = onBack)
        return
    }
    if (bootstrapState is ClassifierBootstrapState.Initializing) {
        // The photo model is (re)building — e.g. a retry() from the error view above. Don't
        // call graph.scanViewModel() yet (it requires Ready); show an in-place loader scoped to
        // this screen instead of AppGate's full-screen one, so Scan stays on the back stack.
        PhotoModelStartingView(onBack = onBack)
        return
    }
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
