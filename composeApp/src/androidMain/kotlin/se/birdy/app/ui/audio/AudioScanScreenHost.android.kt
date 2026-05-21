package se.birdy.app.ui.audio

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import se.birdy.app.di.AppGraph

@Composable
actual fun AudioScanScreenHost(
    graph: AppGraph,
    onNavigateToMatch: (String) -> Unit,
    onBack: () -> Unit,
) {
    val activity = LocalContext.current as ComponentActivity
    val permissionController = remember(activity) { AndroidAudioPermissionController(activity) }
    val permissionState by permissionController.state.collectAsState()

    val vm = remember(graph) { graph.audioScanViewModel() }
    val state by vm.state.collectAsState()

    LaunchedEffect(permissionState) {
        vm.onPermissionState(permissionState)
    }

    LaunchedEffect(state) {
        val s = state
        if (s is AudioScanState.NavigateToMatch) onNavigateToMatch(s.sourceJson)
    }

    AudioScanScreen(
        state = state,
        permissionState = permissionState,
        onStartHold = vm::startRecording,
        onReleaseHold = { /* state machine handles 3 s auto-complete */ },
        onCancel = vm::cancelRecording,
        onRequestPermission = permissionController::request,
        onOpenSettings = permissionController::openSettings,
        onRetry = vm::cancelRecording,
        onBack = onBack,
    )
}
