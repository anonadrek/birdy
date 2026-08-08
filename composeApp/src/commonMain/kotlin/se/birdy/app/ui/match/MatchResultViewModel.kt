package se.birdy.app.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import se.birdy.app.photo.FrameUnavailableException
import se.birdy.app.ui.badges.UnlockQueue
import se.birdy.app.usecase.SaveObservationUseCase
import se.birdy.app.util.ReadFailureKind
import se.birdy.app.util.classifyReadFailure
import se.birdy.app.util.ioDispatcher
import se.birdy.app.util.readFileBytes
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.observation.ObservationRepository
import se.birdy.domain.observation.ObservationSource
import se.birdy.ml.ScanSource

/**
 * Every capture attaches the current device location, gated by the opt-in location toggle.
 * Photo uploads (gallery + take-photo) use the current location like live scans: the Android
 * photo picker strips GPS EXIF without ACCESS_MEDIA_LOCATION, so "where the photo was taken"
 * isn't reliably available, and "here, now" is the sensible location for an actively-logged find.
 */
fun shouldAttachLocation(source: ScanSource): Boolean =
    when (source) {
        is ScanSource.Audio -> true
        is ScanSource.Image -> true
    }

class MatchResultViewModel(
    private val repository: SpeciesRepository,
    private val observationRepo: ObservationRepository,
    private val saveUseCase: SaveObservationUseCase,
    private val catalog: BadgeCatalog,
    val source: ScanSource,
    private val capturedAtMs: Long,
    private val locale: Locale,
    private val matchOverrideReader: (() -> MatchOverride?)? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<MatchResultUiState>(MatchResultUiState.Loading)
    val state: StateFlow<MatchResultUiState> = _state.asStateFlow()

    private val unlockQueue = UnlockQueue()
    private val thresholds = MatchThresholds.forSource(source)

    init {
        viewModelScope.launch { resolve() }
        viewModelScope.launch {
            // Match-state guard: collector emits emptyList immediately at subscription;
            // resolve() may not have set Match yet, and unlocks only make sense on Match.
            unlockQueue.queue.collect { list ->
                val current = _state.value
                if (current is MatchResultUiState.Match) {
                    val first = list.firstOrNull()
                    _state.value =
                        current.copy(
                            pendingUnlock = first,
                            pendingBadge = first?.let { catalog.findById(it.badgeId) },
                            unlockQueueSize = list.size,
                        )
                }
            }
        }
    }

    private suspend fun resolve() {
        // Read classification results from ScanSource (replaces parseCsv)
        val parsed = source.classification.results.map { it.speciesId to it.confidence }
        if (parsed.isEmpty()) {
            // Empty results = the model scored nothing above noise for any mapped species,
            // regardless of source — a real "no bird here", not a failure. (Image used to
            // route to Error: a garbage photo the model correctly scored as background got
            // an error screen instead of NoBird.) Decode/runtime failures surface as error
            // states upstream of this ViewModel, before a ScanSource is ever constructed —
            // they never reach resolve() as an empty results list.
            _state.value =
                MatchResultUiState.NoBird(
                    frameJpegPath = source.frameJpegPath.ifBlank { null },
                    capturedAtMs = capturedAtMs,
                    source = source,
                    topPrediction = null,
                )
            return
        }
        val resolved = mutableListOf<ResolvedPrediction>()
        for ((id, conf) in parsed) {
            val species =
                runCatching { repository.getById(SpeciesId(id), locale).first() }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
            if (species != null) resolved += ResolvedPrediction(species, conf)
        }
        if (resolved.isEmpty()) {
            _state.value = MatchResultUiState.Error(MatchResultUiState.Error.Kind.ParseFailed)
            return
        }
        val override = matchOverrideReader?.invoke()
        val effective: List<ResolvedPrediction> =
            if (override != null) {
                val species =
                    runCatching { repository.getById(SpeciesId(override.qid), locale).first() }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                if (species != null) {
                    listOf(ResolvedPrediction(species, override.confidence)) +
                        resolved.drop(1).take(2)
                } else {
                    resolved
                }
            } else {
                resolved
            }
        val top1 = effective.first()
        val frameJpegPath = source.frameJpegPath.ifBlank { null }
        val dbLookup =
            runCatching {
                val stamp = observationRepo.nextStampNumber()
                val qid = top1.species.id.raw
                val priorCount = observationRepo.countByQid(qid)
                val prev = if (priorCount > 0) observationRepo.firstByQid(qid) else null
                Triple(stamp, priorCount, prev)
            }.onFailure { if (it is CancellationException) throw it }
                .getOrNull()
        if (dbLookup == null) {
            _state.value = MatchResultUiState.Error(MatchResultUiState.Error.Kind.ParseFailed)
            return
        }
        val (stampNumber, priorCount, prev) = dbLookup
        _state.value =
            when (thresholds.routeFor(top1.confidence)) {
                MatchRoute.MATCH -> {
                    MatchResultUiState.Match(
                        species = top1.species,
                        confidence = top1.confidence,
                        isManualPick = false,
                        isFirstSighting = priorCount == 0,
                        prevObservedAt = prev,
                        sightingCount = priorCount + 1,
                        stampNumber = stampNumber,
                        frameJpegPath = frameJpegPath,
                        capturedAtMs = capturedAtMs,
                        source = source,
                    )
                }
                MatchRoute.DISAMBIG ->
                    MatchResultUiState.Disambig(
                        candidates =
                            effective
                                .filter { it.confidence >= thresholds.disambigConfidence }
                                .take(3),
                        stampNumber = stampNumber,
                        frameJpegPath = frameJpegPath,
                        capturedAtMs = capturedAtMs,
                        source = source,
                    )
                MatchRoute.NOBIRD ->
                    MatchResultUiState.NoBird(
                        frameJpegPath = frameJpegPath,
                        capturedAtMs = capturedAtMs,
                        source = source,
                        topPrediction = top1.takeIf { it.confidence >= thresholds.noBirdHintFloor },
                    )
            }
    }

    fun pickFromDisambig(speciesId: SpeciesId) {
        val current = _state.value as? MatchResultUiState.Disambig ?: return
        val picked = current.candidates.firstOrNull { it.species.id == speciesId } ?: return
        viewModelScope.launch {
            val lookup =
                runCatching {
                    val c = observationRepo.countByQid(speciesId.raw)
                    val p: Instant? = if (c > 0) observationRepo.firstByQid(speciesId.raw) else null
                    c to p
                }.onFailure { if (it is CancellationException) throw it }
                    .getOrNull() ?: (0 to null as Instant?)
            val (count, prev) = lookup
            _state.value =
                MatchResultUiState.Match(
                    species = picked.species,
                    confidence = picked.confidence,
                    isManualPick = true,
                    isFirstSighting = count == 0,
                    prevObservedAt = prev,
                    sightingCount = count + 1,
                    stampNumber = current.stampNumber,
                    frameJpegPath = current.frameJpegPath,
                    capturedAtMs = current.capturedAtMs,
                    source = current.source,
                )
        }
    }

    fun saveToDiary(note: String = "") {
        val current = _state.value as? MatchResultUiState.Match ?: return
        if (current.saveStatus is MatchResultUiState.SaveStatus.Saving ||
            current.saveStatus is MatchResultUiState.SaveStatus.Saved
        ) {
            return
        }
        val path = current.frameJpegPath
        if (path == null) {
            _state.value =
                current.copy(
                    saveStatus =
                        MatchResultUiState.SaveStatus.Failed(
                            MatchResultUiState.SaveStatus.Failed.Kind.FrameUnavailable,
                        ),
                )
            return
        }
        val audioPath = (current.source as? ScanSource.Audio)?.audioWavPath
        val sourceType =
            if (current.source is ScanSource.Audio) ObservationSource.Audio else ObservationSource.Photo
        _state.value = current.copy(saveStatus = MatchResultUiState.SaveStatus.Saving)
        viewModelScope.launch {
            val outcome =
                runCatching {
                    val bytes = withContext(ioDispatcher) { readFileBytes(path) }
                    saveUseCase.save(
                        speciesId = current.species.id.raw,
                        capturedAt = Instant.fromEpochMilliseconds(current.capturedAtMs),
                        confidence = current.confidence,
                        rawJpegBytes = bytes,
                        note = note,
                        audioPath = audioPath,
                        sourceType = sourceType,
                        attachLocation = shouldAttachLocation(current.source),
                    )
                }.onFailure { if (it is CancellationException) throw it }
            val status: MatchResultUiState.SaveStatus =
                outcome.fold(
                    onSuccess = { result ->
                        if (result.newUnlocks.isNotEmpty()) unlockQueue.enqueue(result.newUnlocks)
                        MatchResultUiState.SaveStatus.Saved
                    },
                    onFailure = { t ->
                        val kind =
                            when {
                                t is FrameUnavailableException ->
                                    MatchResultUiState.SaveStatus.Failed.Kind.FrameUnavailable
                                classifyReadFailure(t) == ReadFailureKind.NOT_FOUND ->
                                    MatchResultUiState.SaveStatus.Failed.Kind.FrameUnavailable
                                classifyReadFailure(t) == ReadFailureKind.IO_ERROR ->
                                    MatchResultUiState.SaveStatus.Failed.Kind.StorageFull
                                else ->
                                    MatchResultUiState.SaveStatus.Failed.Kind.DatabaseFailed
                            }
                        MatchResultUiState.SaveStatus.Failed(kind)
                    },
                )
            val latest = _state.value
            if (latest is MatchResultUiState.Match) {
                _state.value = latest.copy(saveStatus = status)
            }
        }
    }

    fun dismissUnlock() = unlockQueue.pop()

    /**
     * Save the current frame as an unidentified observation (species_id = null).
     * Used from Disambig when the user can't pick a candidate but still wants to
     * archive the sighting. Confidence is recorded as 0f and badge-recalc is
     * skipped (no species → no rule matches).
     */
    fun saveAsUnknown() {
        val current = _state.value as? MatchResultUiState.Disambig ?: return
        val path = current.frameJpegPath ?: return
        val audioPath = (current.source as? ScanSource.Audio)?.audioWavPath
        val sourceType =
            if (current.source is ScanSource.Audio) ObservationSource.Audio else ObservationSource.Photo
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(ioDispatcher) { readFileBytes(path) }
                saveUseCase.save(
                    speciesId = null,
                    capturedAt = Instant.fromEpochMilliseconds(current.capturedAtMs),
                    confidence = 0f,
                    rawJpegBytes = bytes,
                    note = "",
                    audioPath = audioPath,
                    sourceType = sourceType,
                    attachLocation = shouldAttachLocation(current.source),
                )
            }.onFailure { if (it is CancellationException) throw it }
        }
    }
}
