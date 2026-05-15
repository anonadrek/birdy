package se.birdy.app.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.observation.ObservationRepository
import java.io.File
import java.io.IOException

class MatchResultViewModel(
    private val repository: SpeciesRepository,
    private val observationRepo: ObservationRepository,
    private val saveUseCase: SaveObservationUseCase,
    private val catalog: BadgeCatalog,
    private val predictionsCsv: String,
    private val frameJpegPath: String?,
    private val capturedAtMs: Long,
    private val locale: Locale,
) : ViewModel() {
    private val _state = MutableStateFlow<MatchResultUiState>(MatchResultUiState.Loading)
    val state: StateFlow<MatchResultUiState> = _state.asStateFlow()

    private val unlockQueue = UnlockQueue()

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
        val parsed = parseCsv(predictionsCsv)
        if (parsed.isEmpty()) {
            _state.value = MatchResultUiState.Error(MatchResultUiState.Error.Kind.NoPredictions)
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
        val top1 = resolved.first()
        val stampNumber = observationRepo.nextStampNumber()
        _state.value =
            when (MatchThresholds.routeFor(top1.confidence)) {
                MatchRoute.MATCH -> {
                    val qid = top1.species.id.raw
                    val priorCount = observationRepo.countByQid(qid)
                    val prev = if (priorCount > 0) observationRepo.firstByQid(qid) else null
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
                    )
                }
                MatchRoute.DISAMBIG ->
                    MatchResultUiState.Disambig(
                        candidates =
                            resolved
                                .filter { it.confidence >= MatchThresholds.DISAMBIG_CONFIDENCE }
                                .take(3),
                        stampNumber = stampNumber,
                        frameJpegPath = frameJpegPath,
                        capturedAtMs = capturedAtMs,
                    )
                MatchRoute.NOBIRD ->
                    MatchResultUiState.NoBird(
                        frameJpegPath = frameJpegPath,
                        capturedAtMs = capturedAtMs,
                        topPrediction = top1.takeIf { it.confidence >= 0.15f },
                    )
            }
    }

    private fun parseCsv(csv: String): List<Pair<String, Float>> =
        csv.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val id = parts[0].trim()
            val confParts = parts[1].split("/")
            val numerator = confParts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val denominator = confParts.getOrNull(1)?.toIntOrNull() ?: 100
            if (id.isBlank()) return@mapNotNull null
            if (denominator <= 0 || numerator < 0) return@mapNotNull null
            val conf = (numerator.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)
            id to conf
        }

    fun pickFromDisambig(speciesId: SpeciesId) {
        val current = _state.value as? MatchResultUiState.Disambig ?: return
        val picked = current.candidates.firstOrNull { it.species.id == speciesId } ?: return
        viewModelScope.launch {
            val count = observationRepo.countByQid(speciesId.raw)
            val prev = if (count > 0) observationRepo.firstByQid(speciesId.raw) else null
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
        _state.value = current.copy(saveStatus = MatchResultUiState.SaveStatus.Saving)
        viewModelScope.launch {
            val outcome =
                runCatching {
                    val bytes = withContext(Dispatchers.IO) { File(path).readBytes() }
                    saveUseCase.save(
                        speciesId = current.species.id.raw,
                        capturedAt = Instant.fromEpochMilliseconds(current.capturedAtMs),
                        confidence = current.confidence,
                        rawJpegBytes = bytes,
                        note = note,
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
                            when (t) {
                                is FrameUnavailableException ->
                                    MatchResultUiState.SaveStatus.Failed.Kind.FrameUnavailable
                                is java.io.FileNotFoundException ->
                                    MatchResultUiState.SaveStatus.Failed.Kind.FrameUnavailable
                                is IOException ->
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
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { File(path).readBytes() }
                saveUseCase.save(
                    speciesId = null,
                    capturedAt = Instant.fromEpochMilliseconds(current.capturedAtMs),
                    confidence = 0f,
                    rawJpegBytes = bytes,
                    note = "",
                )
            }.onFailure { if (it is CancellationException) throw it }
        }
    }
}
