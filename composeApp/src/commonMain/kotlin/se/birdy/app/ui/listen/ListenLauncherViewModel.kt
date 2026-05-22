package se.birdy.app.ui.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface ListenLauncherEffect {
    data object NavigateToAudioScan : ListenLauncherEffect
}

class ListenLauncherViewModel : ViewModel() {
    private val _effects =
        MutableSharedFlow<ListenLauncherEffect>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val effects: SharedFlow<ListenLauncherEffect> = _effects.asSharedFlow()

    fun onAudioCardTap() {
        viewModelScope.launch {
            _effects.emit(ListenLauncherEffect.NavigateToAudioScan)
        }
    }
}
