package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import se.birdy.ml.CameraSource

@Composable
expect fun CameraPreviewHost(
    cameraSource: CameraSource,
    modifier: Modifier = Modifier,
)
