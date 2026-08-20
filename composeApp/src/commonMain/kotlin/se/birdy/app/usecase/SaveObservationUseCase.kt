package se.birdy.app.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.location.LatLng
import se.birdy.app.location.LocationProvider
import se.birdy.app.photo.PhotoStorage
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import se.birdy.domain.observation.ObservationSource
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
    private val onObservationSaved: (suspend (Observation) -> Unit)? = null,
    private val dailyBirdMatchCount: suspend () -> Int = { 0 },
    private val locationProvider: LocationProvider? = null,
    private val locationEnabled: suspend () -> Boolean = { false },
) {
    @OptIn(ExperimentalUuidApi::class)
    @Suppress("LongParameterList")
    suspend fun save(
        speciesId: String?,
        capturedAt: Instant,
        confidence: Float,
        rawJpegBytes: ByteArray,
        note: String,
        audioPath: String? = null,
        sourceType: ObservationSource = ObservationSource.Photo,
        attachLocation: Boolean = false,
    ): SaveResult {
        val id = Uuid.random().toString()
        val nextStamp = repo.nextStampNumber()
        val photoPath = photoStorage.persistJpeg(rawJpegBytes)

        val latLng: LatLng? =
            if (attachLocation && locationEnabled()) {
                runCatching { locationProvider?.current() }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
            } else {
                null
            }

        val observation =
            Observation(
                id = id,
                speciesId = speciesId,
                capturedAt = capturedAt,
                savedAt = clock.now(),
                photoPath = photoPath,
                note = note,
                confidence = confidence,
                latitude = latLng?.latitude,
                longitude = latLng?.longitude,
                locationLabel = null,
                stampNumber = nextStamp,
                audioPath = audioPath,
                sourceType = sourceType,
            )

        try {
            repo.insert(observation)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            runCatching { photoStorage.delete(photoPath) }
            throw t
        }

        // Insert is the commit boundary. Daily-bird marking / in-app review must not
        // fail the save: the UI treats any thrown error as "not saved" and the user
        // retries → a duplicate diary row. Cancellation still propagates.
        try {
            onObservationSaved?.invoke(observation)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
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
                val matchCount =
                    runCatching { dailyBirdMatchCount() }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrDefault(0)
                val computed = recalculate.newUnlocks(allObs, species, catalog, existing, dailyBirdMatchCount = matchCount)
                badgeRepo.persist(computed)
                computed
            }.onFailure {
                if (it is CancellationException) throw it
            }.getOrDefault(emptyList())

        return SaveResult(observationId = id, newUnlocks = newUnlocks)
    }
}
