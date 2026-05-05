package se.birdy.app.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository

class ClassificationResultViewModel(
    private val repository: SpeciesRepository,
    private val predictionsCsv: String,
    private val frameJpegPath: String?,
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
                runCatching {
                    repository.getById(SpeciesId(id), locale).first()
                }.onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
            if (species == null) {
                unresolved += id
            } else {
                resolved += ResolvedPrediction(species, conf)
            }
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
