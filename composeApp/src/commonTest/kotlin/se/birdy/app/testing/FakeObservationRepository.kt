package se.birdy.app.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository

class FakeObservationRepository : ObservationRepository {
    private val _rows = MutableStateFlow<List<Observation>>(emptyList())
    val rows: Flow<List<Observation>> get() = _rows.asStateFlow()

    val allInserted: List<Observation> get() = _rows.value
    val lastInserted: Observation? get() = _rows.value.lastOrNull()

    /** Hook to simulate insert-fail for SaveObservationUseCaseTest. */
    var failOnInsert: Throwable? = null

    /** Hook to simulate updateNote-fail for ObservationDetailViewModelTest. */
    var failOnUpdateNote: Throwable? = null

    /** Hook to simulate delete-fail for ObservationDetailViewModelTest. */
    var failOnDelete: Throwable? = null

    override fun observeAll(): Flow<List<Observation>> =
        _rows.asStateFlow().map { it.sortedByDescending { o -> o.capturedAt.toEpochMilliseconds() } }

    override fun observeAllByStampNumber(): Flow<List<Observation>> =
        _rows.asStateFlow().map { it.sortedByDescending { o -> o.stampNumber } }

    override fun observeById(id: String): Flow<Observation?> = _rows.asStateFlow().map { list -> list.firstOrNull { it.id == id } }

    override suspend fun insert(observation: Observation) {
        failOnInsert?.let { throw it }
        _rows.value = _rows.value + observation
    }

    override suspend fun updateNote(
        id: String,
        note: String,
    ) {
        failOnUpdateNote?.let { throw it }
        _rows.value = _rows.value.map { if (it.id == id) it.copy(note = note) else it }
    }

    override suspend fun delete(id: String) {
        failOnDelete?.let { throw it }
        _rows.value = _rows.value.filter { it.id != id }
    }

    override suspend fun nextStampNumber(): Int = (_rows.value.maxOfOrNull { it.stampNumber } ?: 0) + 1

    override suspend fun countByQid(speciesId: String): Int = _rows.value.count { it.speciesId == speciesId }

    override suspend fun firstByQid(speciesId: String): Instant? =
        _rows.value
            .filter { it.speciesId == speciesId }
            .minByOrNull { it.capturedAt }
            ?.capturedAt

    fun seed(observations: List<Observation>) {
        _rows.value = observations
    }

    fun seedDirect(obs: Observation) {
        _rows.value = _rows.value + obs
    }

    fun seedObservation(
        speciesId: String,
        capturedAt: Instant,
        id: String = "obs-${capturedAt.toEpochMilliseconds()}",
    ) {
        val obs =
            Observation(
                id = id,
                speciesId = speciesId,
                capturedAt = capturedAt,
                savedAt = capturedAt,
                photoPath = "/tmp/$id.jpg",
                note = "",
                confidence = 0.9f,
                latitude = null,
                longitude = null,
                locationLabel = null,
            )
        _rows.value = _rows.value + obs
    }

    companion object {
        /** Pre-populerad med 5 observationer över 2 månader för VM-tester. */
        fun withDefaults(): FakeObservationRepository {
            val repo = FakeObservationRepository()
            val mayBase = Instant.parse("2026-05-03T11:08:00Z").toEpochMilliseconds()
            val aprBase = Instant.parse("2026-04-29T08:55:00Z").toEpochMilliseconds()
            repo._rows.value =
                listOf(
                    sample("o1", "Q25485", mayBase + 6 * 24 * 3600 * 1000L), // 9 maj
                    sample("o2", "Q25234", mayBase + 5 * 24 * 3600 * 1000L), // 8 maj
                    sample("o3", "Q25404", mayBase), // 3 maj
                    sample("o4", "Q25402", mayBase - 24 * 3600 * 1000L), // 2 maj
                    sample("o5", "Q26490", aprBase), // 29 apr
                )
            return repo
        }

        private fun sample(
            id: String,
            speciesId: String,
            capturedAtMs: Long,
        ) = Observation(
            id = id,
            speciesId = speciesId,
            capturedAt = Instant.fromEpochMilliseconds(capturedAtMs),
            savedAt = Instant.fromEpochMilliseconds(capturedAtMs + 60_000),
            photoPath = "/data/.../$id.jpg",
            note = "",
            confidence = 0.87f,
            latitude = null,
            longitude = null,
            locationLabel = null,
        )
    }
}
