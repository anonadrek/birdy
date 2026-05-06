package se.birdy.app.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository

class DiaryViewModel(
    private val obsRepo: ObservationRepository,
    private val speciesRepo: SpeciesRepository,
    private val locale: Locale,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    val state: StateFlow<DiaryUiState> =
        obsRepo
            .observeAll()
            .map { observations -> buildState(observations) }
            .catch { t ->
                if (t is CancellationException) throw t
                emit(DiaryUiState.Loading)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DiaryUiState.Loading,
            )

    private suspend fun buildState(observations: List<Observation>): DiaryUiState {
        if (observations.isEmpty()) return DiaryUiState.Empty
        val now = clock.now()
        val items =
            observations.map { obs ->
                val name =
                    runCatching {
                        speciesRepo.getById(SpeciesId(obs.speciesId), locale).first()?.name
                    }.onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                        ?: DiaryItem.UNKNOWN_SPECIES_PLACEHOLDER
                DiaryItem(
                    observationId = obs.id,
                    speciesName = name,
                    photoPath = obs.photoPath,
                    confidencePct = (obs.confidence * 100f).toInt().coerceIn(0, 100),
                    relativeDate = relativeDate(obs.capturedAt, now, timeZone),
                )
            }
        val groups = mutableListOf<MonthGroup>()
        items
            .groupBy { item ->
                val date = observations.first { it.id == item.observationId }.capturedAt.toLocalDateTime(timeZone)
                date.year to date.monthNumber
            }.forEach { (key, list) ->
                groups += MonthGroup(year = key.first, month1Based = key.second, items = list)
            }
        // observeAll() returnerar redan DESC på captured_at_ms — gruppering bibehåller ordningen.
        return DiaryUiState.Loaded(months = groups)
    }
}
