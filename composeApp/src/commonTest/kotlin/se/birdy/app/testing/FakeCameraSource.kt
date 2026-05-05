package se.birdy.app.testing

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import se.birdy.ml.CameraSource
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput

class FakeCameraSource : CameraSource {
    private val frameFlow =
        MutableSharedFlow<ImageInput>(
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    var startCalls: Int = 0
        private set
    var stopCalls: Int = 0
        private set

    override fun frames(): Flow<ImageInput> = frameFlow

    override suspend fun start() {
        startCalls += 1
    }

    override suspend fun stop() {
        stopCalls += 1
    }

    suspend fun emit(timestampMillis: Long) {
        frameFlow.emit(
            ImageInput(
                bytes = byteArrayOf(),
                widthPx = 224,
                heightPx = 224,
                format = FrameFormat.JPEG,
                timestampMillis = timestampMillis,
            ),
        )
    }
}
