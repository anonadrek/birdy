package se.birdy.app.ui.scan

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import se.birdy.ml.CameraSource
import se.birdy.ml.camera.AndroidCameraSource

@Composable
actual fun CameraPreviewHost(
    cameraSource: CameraSource,
    modifier: Modifier,
) {
    val androidSource = cameraSource as? AndroidCameraSource ?: return
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                androidSource.bindPreview(view)
            }
        },
    )
}
