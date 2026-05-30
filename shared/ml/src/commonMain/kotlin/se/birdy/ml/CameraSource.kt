package se.birdy.ml

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Delad no-op-flow så default-impl inte allokerar en ny StateFlow per access.
private val NO_ZOOM: StateFlow<ZoomState> = MutableStateFlow(ZoomState.NONE)

interface CameraSource {
    fun frames(): Flow<ImageInput>

    suspend fun start()

    suspend fun stop()

    /** Kamerans zoom-läge. Default = ingen zoom (för fakes/test-impl). */
    val zoom: StateFlow<ZoomState>
        get() = NO_ZOOM

    /** Sätt zoom-ratio (klampas mot enhetens min/max av implementationen). Default = no-op. */
    fun setZoomRatio(ratio: Float) {}
}
