package se.birdy.app.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.birdy.app.photo.PhotoStorage
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.domain.observation.ObservationRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ObservationDetailViewModel(
    private val id: String,
    private val obsRepo: ObservationRepository,
    private val speciesRepo: SpeciesRepository,
    private val photoStorage: PhotoStorage,
    private val locale: Locale,
) : ViewModel() {
    private val _effects = Channel<DetailEffect>(Channel.BUFFERED)
    val effects: Flow<DetailEffect> = _effects.receiveAsFlow()

    // Tracks whether a note-save is in progress; combined into `state` so Loaded.noteSaving is wired.
    private val noteSaving = MutableStateFlow(false)

    val state: StateFlow<ObservationDetailUiState> =
        combine(
            obsRepo
                .observeById(id)
                .flatMapLatest { obs ->
                    if (obs == null) {
                        flowOf<ObservationDetailUiState>(ObservationDetailUiState.NotFound)
                    } else {
                        flow {
                            val qid = obs.speciesId
                            val species =
                                if (qid != null) {
                                    runCatching {
                                        speciesRepo.getById(SpeciesId(qid), locale).first()
                                    }.onFailure { if (it is CancellationException) throw it }
                                        .getOrNull()
                                } else {
                                    null
                                }
                            emit(ObservationDetailUiState.Loaded(obs, species))
                        }
                    }
                }.catch { t ->
                    if (t is CancellationException) throw t
                    emit(ObservationDetailUiState.Error(ObservationDetailUiState.Error.Kind.LoadFailed))
                },
            noteSaving,
        ) { base, saving ->
            if (base is ObservationDetailUiState.Loaded) base.copy(noteSaving = saving) else base
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ObservationDetailUiState.Loading,
        )

    fun saveNote(text: String) {
        viewModelScope.launch {
            noteSaving.value = true
            val outcome =
                runCatching { obsRepo.updateNote(id, text) }
                    .onFailure { if (it is CancellationException) throw it }
            noteSaving.value = false
            _effects.trySend(DetailEffect.NoteSaved(success = outcome.isSuccess))
        }
    }

    fun delete() {
        viewModelScope.launch {
            val current = obsRepo.observeById(id).first() ?: return@launch
            runCatching { obsRepo.delete(id) }
                .onFailure {
                    if (it is CancellationException) throw it
                    _effects.trySend(DetailEffect.DeleteFailed)
                }.onSuccess {
                    runCatching { photoStorage.delete(current.photoPath) }
                        .onFailure { if (it is CancellationException) throw it }
                    _effects.trySend(DetailEffect.Deleted)
                }
        }
    }
}
