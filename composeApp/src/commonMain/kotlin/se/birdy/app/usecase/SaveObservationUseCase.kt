package se.birdy.app.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.photo.PhotoStorage
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SaveObservationUseCase(
    private val repo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val photoStorage: PhotoStorage,
    private val clock: Clock,
    private val catalog: BadgeCatalog,
    private val recalculate: RecalculateBadgesUseCase,
    private val speciesByQid: suspend () -> Map<SpeciesId, Species>,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun save(
        speciesId: String?,
        capturedAt: Instant,
        confidence: Float,
        rawJpegBytes: ByteArray,
        note: String,
    ): SaveResult {
        val id = Uuid.random().toString()
        val nextStamp = repo.nextStampNumber()
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
                    stampNumber = nextStamp,
                ),
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            runCatching { photoStorage.delete(photoPath) }
            throw t
        }

        val newUnlocks =
            runCatching {
                val allObs = repo.observeAll().first()
                val species = speciesByQid()
                val existing =
                    badgeRepo
                        .observeUnlocks()
                        .first()
                        .map { it.badgeId }
                        .toSet()
                val computed = recalculate.newUnlocks(allObs, species, catalog, existing)
                badgeRepo.persist(computed)
                computed
            }.onFailure {
                if (it is CancellationException) throw it
            }.getOrDefault(emptyList())

        return SaveResult(observationId = id, newUnlocks = newUnlocks)
    }
}
