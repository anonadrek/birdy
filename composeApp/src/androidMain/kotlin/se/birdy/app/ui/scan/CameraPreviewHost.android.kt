package se.birdy.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import se.birdy.ml.CameraSource

@Composable
actual fun CameraPreviewHost(
    cameraSource: CameraSource,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black))
}
