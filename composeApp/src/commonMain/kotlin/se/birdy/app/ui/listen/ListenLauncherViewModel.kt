package se.birdy.app.ui.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.birdy.domain.premium.PremiumState

sealed interface ListenLauncherEffect {
    data object NavigateToAudioScan : ListenLauncherEffect

    data object NavigateToPremium : ListenLauncherEffect
}

class ListenLauncherViewModel(
    premiumStateFlow: Flow<PremiumState>,
) : ViewModel() {
    val premiumActive: StateFlow<Boolean> =
        premiumStateFlow
            .map { it is PremiumState.Active }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _effects =
        MutableSharedFlow<ListenLauncherEffect>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val effects: SharedFlow<ListenLauncherEffect> = _effects.asSharedFlow()

    fun onAudioCardTap() {
        viewModelScope.launch {
            val effect =
                if (premiumActive.value) {
                    ListenLauncherEffect.NavigateToAudioScan
                } else {
                    ListenLauncherEffect.NavigateToPremium
                }
            _effects.emit(effect)
        }
    }
}
