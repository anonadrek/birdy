package se.birdy.app.ui.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface ListenLauncherEvent {
    data object AudioLockedSnackbar : ListenLauncherEvent
}

class ListenLauncherViewModel : ViewModel() {
    private val _events =
        MutableSharedFlow<ListenLauncherEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<ListenLauncherEvent> = _events.asSharedFlow()

    fun onAudioLockedTap() {
        viewModelScope.launch { _events.emit(ListenLauncherEvent.AudioLockedSnackbar) }
    }
}
