package se.birdy.app.ui.audio

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import se.birdy.app.di.AppGraph

@Composable
actual fun AudioScanScreenHost(
    graph: AppGraph,
    onNavigateToMatch: (String) -> Unit,
    onBack: () -> Unit,
) {
    val activity = LocalContext.current as ComponentActivity

    // launchTrigger bridges the chicken-and-egg between rememberLauncherForActivityResult
    // (must be called at composition time) and the controller (needs to invoke the launcher).
    val launchTrigger = remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionController =
        remember(activity) {
            AndroidAudioPermissionController(activity) { launchTrigger.value?.invoke() }
        }

    // rememberLauncherForActivityResult is called during composition — correct timing per AndroidX.
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> permissionController.onResult(granted) }

    // Wire the launcher into the trigger once it is stable.
    LaunchedEffect(launcher) {
        launchTrigger.value = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }

    // Recheck permission on every ON_RESUME so we pick up changes made in system settings.
    // DisposableEffect removes the observer when the composable leaves composition — no leak.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) permissionController.recheck()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        onCancel = vm::cancelRecording,
        onRequestPermission = permissionController::request,
        onOpenSettings = permissionController::openSettings,
        onRetry = vm::cancelRecording,
        onBack = onBack,
    )
}
