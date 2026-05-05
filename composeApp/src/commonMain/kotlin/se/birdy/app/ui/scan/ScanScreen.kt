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
import se.birdy.ml.CameraSource

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    cameraSource: CameraSource,
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (predictionsCsv: String, frameJpegPath: String) -> Unit,
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
            onFrozen(csv, s.frameJpegPath)
            viewModel.onResumeAfterFreeze()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is ScanUiState.PermissionRequired -> PermissionRequiredView(onAllow = onPermissionRequest)
            is ScanUiState.PermissionDenied -> PermissionDeniedView(onOpenSettings = onOpenSettings)
            is ScanUiState.Error -> ErrorView(s.message)
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
                    val name = s.top1?.speciesId ?: "söker…"
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
                    Text(text = "TRYCK VAR SOM HELST FÖR ATT FRYSA", color = Color(0xFFF0EAD8))
                    OutlinedButton(onClick = onPhotoAnalyzeClick) {
                        Text("Analysera ett foto")
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
        Text("Birdy behöver tillgång till kameran för att skanna fåglar.", color = Color(0xFFF0EAD8))
        Box(modifier = Modifier.size(16.dp))
        Button(onClick = onAllow) { Text("Tillåt kamera") }
    }
}

@Composable
private fun PermissionDeniedView(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Kameran är blockerad. Aktivera i inställningar för att skanna.", color = Color(0xFFF0EAD8))
        Box(modifier = Modifier.size(16.dp))
        Button(onClick = onOpenSettings) { Text("Öppna inställningar") }
    }
}

@Composable
private fun ErrorView(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Color(0xFFF0EAD8))
    }
}
