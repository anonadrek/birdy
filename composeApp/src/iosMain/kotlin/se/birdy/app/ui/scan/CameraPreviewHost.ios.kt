package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import se.birdy.ml.CameraSource

/** Never composed in i0 — ScanScreenHost is stubbed above it. Real AVFoundation preview lands in i2. */
@Composable
actual fun CameraPreviewHost(
    cameraSource: CameraSource,
    modifier: Modifier,
) = Unit
