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
 *
 * [dailyBirdBadgeUnlocked]: whether Dagens fågel-jägare, a Premium badge, is open to this user
 * (AppGraph passes the app's effective Premium state, early users' Lifetime included). When it is
 * not, the hero's challenge row tags the badge as Premium (Albin, 2026-10-07). A label only:
 * nothing on this screen is gated by it, audio ID least of all (BirdNET is NonCommercial, see
 * BirdNetLicenseGuardTest).
 */
class ListenLauncherViewModel(
    val dailyBird: StateFlow<DailyBirdToday?> = MutableStateFlow(null),
    val dailyBirdBadgeUnlocked: StateFlow<Boolean> = MutableStateFlow(true),
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
