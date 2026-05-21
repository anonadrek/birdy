package se.birdy.domain.observation

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Returned by [ObservationRepository.delete] so callers can clean up
 * both the photo and audio files associated with the observation.
 */
data class FileCleanupRequest(
    val photoPath: String?,
    val audioPath: String?,
)

interface ObservationRepository {
    fun observeAll(): Flow<List<Observation>>

    fun observeAllByStampNumber(): Flow<List<Observation>>

    fun observeById(id: String): Flow<Observation?>

    suspend fun insert(observation: Observation)

    suspend fun updateNote(
        id: String,
        note: String,
    )

    suspend fun delete(id: String): FileCleanupRequest

    suspend fun nextStampNumber(): Int

    /** Antal observationer för given Q-ID. 0 = first sighting för Plan 7d Match-flow. */
    suspend fun countByQid(speciesId: String): Int

    /** Tidigaste `captured_at` för given Q-ID, eller null om inga observationer finns. */
    suspend fun firstByQid(speciesId: String): Instant?
}
