@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import se.birdy.ml.CameraSource
import se.birdy.ml.camera.IosCameraSource

/**
 * AVCaptureVideoPreviewLayer i en UIKitView. Downcast-mönstret speglar Android-actualen
 * (CameraPreviewHost.android.kt): preview-lagret får känna till sin konkreta källa.
 * resizeAspectFill = Androids PreviewView.FILL_CENTER. Sessionen är eagert skapad i
 * IosCameraSource, så lagret kan kopplas innan start() hunnit konfigurera den.
 */
@Composable
actual fun CameraPreviewHost(
    cameraSource: CameraSource,
    modifier: Modifier,
) {
    val iosSource = cameraSource as? IosCameraSource ?: return
    UIKitView(
        factory = { CameraPreviewView(iosSource.captureSession) },
        modifier = modifier,
    )
}

private class CameraPreviewView(
    session: AVCaptureSession,
) : UIView(frame = CGRectZero.readValue()) {
    private val previewLayer =
        AVCaptureVideoPreviewLayer(session = session).also {
            it.videoGravity = AVLayerVideoGravityResizeAspectFill
        }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.setFrame(bounds)
        // Connection finns först när sessionen fått sina in-/utgångar (efter start()) —
        // portrait sätts därför lazy här, varje layout-pass är billig och idempotent.
        previewLayer.connection?.let { conn ->
            if (conn.isVideoOrientationSupported()) {
                conn.videoOrientation = AVCaptureVideoOrientationPortrait
            }
        }
        CATransaction.commit()
    }
}
