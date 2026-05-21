package se.birdy.data.observation

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import se.birdy.data.db.ObservationQueries
import se.birdy.domain.observation.FileCleanupRequest
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import se.birdy.domain.observation.ObservationSource
import se.birdy.data.db.Observation as ObservationRow

class SqlDelightObservationRepository(
    private val queries: ObservationQueries,
) : ObservationRepository {
    override fun observeAll(): Flow<List<Observation>> =
        queries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeAllByStampNumber(): Flow<List<Observation>> =
        queries
            .selectAllByStampNumber()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Observation?> =
        queries
            .selectById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() }

    override suspend fun nextStampNumber(): Int =
        withContext(Dispatchers.IO) {
            queries.nextStampNumber().executeAsOne().toInt()
        }

    override suspend fun countByQid(speciesId: String): Int =
        withContext(Dispatchers.IO) {
            queries.countByQid(speciesId).executeAsOne().toInt()
        }

    override suspend fun firstByQid(speciesId: String): Instant? =
        withContext(Dispatchers.IO) {
            queries.firstByQid(speciesId).executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it) }
        }

    override suspend fun insert(observation: Observation) {
        withContext(Dispatchers.IO) {
            queries.insert(
                id = observation.id,
                species_id = observation.speciesId,
                captured_at_ms = observation.capturedAt.toEpochMilliseconds(),
                saved_at_ms = observation.savedAt.toEpochMilliseconds(),
                photo_path = observation.photoPath,
                note = observation.note,
                confidence = observation.confidence.toDouble(),
                latitude = observation.latitude,
                longitude = observation.longitude,
                location_label = observation.locationLabel,
                stamp_number = observation.stampNumber.toLong(),
                audio_path = observation.audioPath,
                source = observation.sourceType.name.lowercase(),
            )
        }
    }

    override suspend fun updateNote(
        id: String,
        note: String,
    ) {
        withContext(Dispatchers.IO) {
            queries.updateNote(note = note, id = id)
        }
    }

    override suspend fun delete(id: String): FileCleanupRequest =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                val row = queries.selectById(id).executeAsOneOrNull()
                queries.deleteById(id)
                FileCleanupRequest(
                    photoPath = row?.photo_path,
                    audioPath = row?.audio_path,
                )
            }
        }

    private fun ObservationRow.toDomain(): Observation =
        Observation(
            id = id,
            speciesId = species_id,
            capturedAt = Instant.fromEpochMilliseconds(captured_at_ms),
            savedAt = Instant.fromEpochMilliseconds(saved_at_ms),
            photoPath = photo_path,
            note = note,
            confidence = confidence.toFloat(),
            latitude = latitude,
            longitude = longitude,
            locationLabel = location_label,
            stampNumber = stamp_number.toInt(),
            audioPath = audio_path,
            sourceType =
                when (source) {
                    "audio" -> ObservationSource.Audio
                    else -> ObservationSource.Photo
                },
        )
}
