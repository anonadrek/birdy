package se.birdy.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.scan_error_classifier_failed
import birdy_bird_scanner.composeapp.generated.resources.scan_freeze_hint
import birdy_bird_scanner.composeapp.generated.resources.scan_permission_allow
import birdy_bird_scanner.composeapp.generated.resources.scan_permission_denied_body
import birdy_bird_scanner.composeapp.generated.resources.scan_permission_open_settings
import birdy_bird_scanner.composeapp.generated.resources.scan_permission_required_body
import birdy_bird_scanner.composeapp.generated.resources.scan_photo_analyze
import birdy_bird_scanner.composeapp.generated.resources.scan_top1_searching
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.ml.CameraSource

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    cameraSource: CameraSource,
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (predictionsCsv: String, frameJpegPath: String, capturedAtMs: Long) -> Unit,
    onPermissionRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onCaptureJpeg: () -> ByteArray,
    persistFrame: (ByteArray) -> String,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        val s = state
        if (s is ScanUiState.FrozenAt) {
            val csv =
                s.predictions.joinToString(",") { p ->
                    p.speciesId + ":" + (p.confidence * 100).toInt() + "/100"
                }
            onFrozen(csv, s.frameJpegPath, s.timestampMillis)
            viewModel.onResumeAfterFreeze()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is ScanUiState.PermissionRequired -> PermissionRequiredView(onAllow = onPermissionRequest)
            is ScanUiState.PermissionDenied -> PermissionDeniedView(onOpenSettings = onOpenSettings)
            is ScanUiState.Error -> ErrorView(s.kind)
            else -> {
                CameraPreviewHost(cameraSource = cameraSource, modifier = Modifier.fillMaxSize())
                Crosshair(
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.pressed }) {
                                            viewModel.onFreeze(onCaptureJpeg(), persistFrame)
                                            break
                                        }
                                    }
                                }
                            },
                )
                if (s is ScanUiState.Scanning) {
                    val pct = s.top1?.confidence?.let { (it * 100).toInt() }
                    val name = s.top1?.speciesId ?: stringResource(Res.string.scan_top1_searching)
                    TopChip(
                        speciesName = name,
                        confidencePct = pct,
                        isThrottled = s.isThrottled,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 60.dp, end = 14.dp),
                    )
                }
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = stringResource(Res.string.scan_freeze_hint), color = TextOnHero)
                    OutlinedButton(onClick = onPhotoAnalyzeClick) {
                        Text(stringResource(Res.string.scan_photo_analyze))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequiredView(onAllow: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.scan_permission_required_body), color = TextOnHero)
        Box(modifier = Modifier.size(16.dp))
        Button(onClick = onAllow) { Text(stringResource(Res.string.scan_permission_allow)) }
    }
}

@Composable
private fun PermissionDeniedView(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.scan_permission_denied_body), color = TextOnHero)
        Box(modifier = Modifier.size(16.dp))
        Button(onClick = onOpenSettings) { Text(stringResource(Res.string.scan_permission_open_settings)) }
    }
}

@Composable
private fun ErrorView(kind: ScanErrorKind) {
    val message =
        when (kind) {
            ScanErrorKind.ClassifierFailed -> stringResource(Res.string.scan_error_classifier_failed)
        }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = TextOnHero)
    }
}
