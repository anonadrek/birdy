package se.birdy.app.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import se.birdy.app.photo.PhotoStorage
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SaveObservationUseCase(
    private val repo: ObservationRepository,
    private val photoStorage: PhotoStorage,
    private val clock: Clock,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun save(
        speciesId: String,
        capturedAt: Instant,
        confidence: Float,
        rawJpegBytes: ByteArray,
        note: String,
    ): String {
        val id = Uuid.random().toString()
        val photoPath = photoStorage.persistJpeg(rawJpegBytes)
        try {
            repo.insert(
                Observation(
                    id = id,
                    speciesId = speciesId,
                    capturedAt = capturedAt,
                    savedAt = clock.now(),
                    photoPath = photoPath,
                    note = note,
                    confidence = confidence,
                    latitude = null,
                    longitude = null,
                    locationLabel = null,
                ),
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            runCatching { photoStorage.delete(photoPath) }
            throw t
        }
        return id
    }
}
