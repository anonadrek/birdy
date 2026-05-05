package se.birdy.ml

import kotlinx.coroutines.flow.Flow

interface CameraSource {
    fun frames(): Flow<ImageInput>

    suspend fun start()

    suspend fun stop()
}
