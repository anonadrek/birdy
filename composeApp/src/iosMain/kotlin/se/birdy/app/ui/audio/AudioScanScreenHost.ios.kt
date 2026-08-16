package se.birdy.app.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import se.birdy.app.di.AppGraph

@Composable
actual fun AudioScanScreenHost(
    graph: AppGraph,
    onNavigateToMatch: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val permissionController = remember { IosAudioPermissionController() }

    // Omkontroll när appen blir aktiv igen — fångar toggle i Inställningar
    // (spegel av Android-hostens ON_RESUME-observer / i2c-kameramönstret).
    DisposableEffect(Unit) {
        val observer =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> permissionController.recheck() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }

    val permissionState by permissionController.state.collectAsState()

    val vm = remember(graph) { graph.audioScanViewModel() }

    // KONTRAKT (AudioScanScreenHost-expecten): stoppa inspelningen när skärmen
    // lämnar kompositionen — annars läcker mikrofonen upp till 60 s.
    DisposableEffect(vm) {
        onDispose { vm.cancelRecording() }
    }

    val state by vm.state.collectAsState()
    val demoMode by vm.demoMode.collectAsState()

    LaunchedEffect(permissionState) {
        vm.onPermissionState(permissionState)
    }

    LaunchedEffect(state) {
        val s = state
        if (s is AudioScanState.NavigateToMatch) onNavigateToMatch(s.sourceJson, s.capturedAtMs)
    }

    AudioScanScreen(
        state = state,
        permissionState = permissionState,
        demoMode = demoMode,
        onStartRecording = vm::startRecording,
        onStopRecording = vm::stopRecording,
        onCancelAnalyzing = vm::cancelRecording,
        onRequestPermission = permissionController::request,
        onOpenSettings = permissionController::openSettings,
        onRetry = vm::cancelRecording,
        onBack = onBack,
    )
}
