package se.birdy.app.ui.scan

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import se.birdy.ml.CameraSource
import se.birdy.ml.ImageInput

/** Emits no frames; the scan UI is stubbed in i0. Real AVFoundation source lands in i2. */
class IosNoopCameraSource : CameraSource {
    override fun frames(): Flow<ImageInput> = emptyFlow()

    override suspend fun start() = Unit

    override suspend fun stop() = Unit
}
