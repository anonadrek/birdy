package se.birdy.app.ui.result

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
import se.birdy.app.usecase.SaveObservationUseCase
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import java.io.File
import java.io.IOException

class ClassificationResultViewModel(
    private val repository: SpeciesRepository,
    private val saveUseCase: SaveObservationUseCase,
    private val predictionsCsv: String,
    private val frameJpegPath: String?,
    private val capturedAtMs: Long,
    private val locale: Locale,
) : ViewModel() {
    private val _state = MutableStateFlow<ClassificationResultUiState>(ClassificationResultUiState.Loading)
    val state: StateFlow<ClassificationResultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { resolve() }
    }

    private suspend fun resolve() {
        val parsed = parseCsv(predictionsCsv)
        if (parsed.isEmpty()) {
            _state.value = ClassificationResultUiState.Error(ClassificationResultUiState.Error.Kind.NoPredictions)
            return
        }
        val resolved = mutableListOf<ResolvedPrediction>()
        val unresolved = mutableListOf<String>()
        for ((id, conf) in parsed) {
            val species =
                runCatching { repository.getById(SpeciesId(id), locale).first() }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
            if (species == null) unresolved += id else resolved += ResolvedPrediction(species, conf)
        }
        if (resolved.isEmpty()) {
            _state.value = ClassificationResultUiState.Error(ClassificationResultUiState.Error.Kind.NoMatches)
            return
        }
        val top1 = resolved.first()
        val runnerUps = resolved.drop(1).take(2)
        _state.value =
            ClassificationResultUiState.Loaded(
                top1 = top1,
                runnerUps = runnerUps,
                frozenFramePath = frameJpegPath,
                unresolved = unresolved,
            )
    }

    fun saveToDiary() {
        val current = _state.value
        if (current !is ClassificationResultUiState.Loaded) return
        if (current.saveStatus is ClassificationResultUiState.SaveStatus.Saving ||
            current.saveStatus is ClassificationResultUiState.SaveStatus.Saved
        ) {
            return
        }
        val path = frameJpegPath
        if (path == null) {
            _state.value = current.copy(saveStatus = saveFailed(ClassificationResultUiState.SaveStatus.Failed.Kind.FrameUnavailable))
            return
        }
        _state.value = current.copy(saveStatus = ClassificationResultUiState.SaveStatus.Saving)
        viewModelScope.launch {
            val outcome =
                runCatching {
                    val bytes = withContext(Dispatchers.IO) { File(path).readBytes() }
                    saveUseCase.save(
                        speciesId = current.top1.species.id.raw,
                        capturedAt = Instant.fromEpochMilliseconds(capturedAtMs),
                        confidence = current.top1.confidence,
                        rawJpegBytes = bytes,
                        note = "",
                    )
                }.onFailure { if (it is CancellationException) throw it }
            val status =
                outcome.fold(
                    onSuccess = { ClassificationResultUiState.SaveStatus.Saved },
                    onFailure = { t ->
                        when (t) {
                            is FrameUnavailableException ->
                                saveFailed(ClassificationResultUiState.SaveStatus.Failed.Kind.FrameUnavailable)
                            is java.io.FileNotFoundException ->
                                saveFailed(ClassificationResultUiState.SaveStatus.Failed.Kind.FrameUnavailable)
                            is IOException ->
                                saveFailed(ClassificationResultUiState.SaveStatus.Failed.Kind.StorageFull)
                            else ->
                                saveFailed(ClassificationResultUiState.SaveStatus.Failed.Kind.DatabaseFailed)
                        }
                    },
                )
            val latest = _state.value
            if (latest is ClassificationResultUiState.Loaded) {
                _state.value = latest.copy(saveStatus = status)
            }
        }
    }

    private fun saveFailed(kind: ClassificationResultUiState.SaveStatus.Failed.Kind) = ClassificationResultUiState.SaveStatus.Failed(kind)

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
