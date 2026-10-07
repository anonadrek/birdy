package se.birdy.app.ui.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import se.birdy.app.dailybird.DailyBirdToday

sealed interface ListenLauncherEffect {
    data object NavigateToAudioScan : ListenLauncherEffect
}

/**
 * The Identify hub. Today's bird comes from the app-wide [se.birdy.app.dailybird.DailyBirdTracker]
 * (release 1.3.0 Task 7d), so the hero, the strips on Mina arter/Uppslagsverk and the tab dot
 * always show the same bird and the same catch state; this ViewModel no longer loads it itself.
 */
class ListenLauncherViewModel(
    val dailyBird: StateFlow<DailyBirdToday?> = MutableStateFlow(null),
) : ViewModel() {
    private val _effects =
        MutableSharedFlow<ListenLauncherEffect>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val effects: SharedFlow<ListenLauncherEffect> = _effects.asSharedFlow()

    /** The Lyssna card and the hero's "Lyssna efter den" both open audio ID. */
    fun onAudioCardTap() {
        viewModelScope.launch {
            _effects.emit(ListenLauncherEffect.NavigateToAudioScan)
        }
    }
}
