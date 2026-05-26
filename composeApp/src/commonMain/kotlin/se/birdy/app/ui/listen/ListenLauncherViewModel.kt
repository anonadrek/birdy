package se.birdy.app.ui.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.dailybird.SeasonTag

sealed interface ListenLauncherEffect {
    data object NavigateToAudioScan : ListenLauncherEffect
}

class ListenLauncherViewModel(
    private val selectDailyBird: (suspend (kotlinx.datetime.LocalDate) -> DailyBird?)? = null,
    private val getSpeciesName: (suspend (String) -> String?)? = null,
    private val recordDailyBirdShown: (suspend (kotlinx.datetime.LocalDate, String) -> Unit)? = null,
) : ViewModel() {
    private val _effects =
        MutableSharedFlow<ListenLauncherEffect>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val effects: SharedFlow<ListenLauncherEffect> = _effects.asSharedFlow()

    data class DailyBirdUi(
        val speciesId: String,
        val name: String,
        val seasonTag: SeasonTag,
    )

    private val _dailyBird = MutableStateFlow<DailyBirdUi?>(null)
    val dailyBird: StateFlow<DailyBirdUi?> = _dailyBird.asStateFlow()

    init {
        viewModelScope.launch {
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            val bird = selectDailyBird?.invoke(today) ?: return@launch
            val name = getSpeciesName?.invoke(bird.speciesId) ?: return@launch
            _dailyBird.value = DailyBirdUi(bird.speciesId, name, bird.seasonTag)
            recordDailyBirdShown?.invoke(today, bird.speciesId)
        }
    }

    fun onAudioCardTap() {
        viewModelScope.launch {
            _effects.emit(ListenLauncherEffect.NavigateToAudioScan)
        }
    }
}
