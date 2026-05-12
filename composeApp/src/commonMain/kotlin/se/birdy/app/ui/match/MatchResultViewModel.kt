package se.birdy.app.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.birdy.app.ui.badges.UnlockQueue
import se.birdy.app.usecase.SaveObservationUseCase
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.observation.ObservationRepository

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
}
