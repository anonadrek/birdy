package se.birdy.domain.observation

import kotlinx.coroutines.flow.Flow

interface ObservationRepository {
    fun observeAll(): Flow<List<Observation>>

    fun observeAllByStampNumber(): Flow<List<Observation>>

    fun observeById(id: String): Flow<Observation?>

    suspend fun insert(observation: Observation)

    suspend fun updateNote(
        id: String,
        note: String,
    )

    suspend fun delete(id: String)

    suspend fun nextStampNumber(): Int
}
