package se.birdy.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.UserPreferences

class SettingsViewModel(
    private val prefs: UserPreferences,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(prefs.userName, prefs.appLanguage) { name, lang ->
            SettingsUiState(userName = name, language = lang)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState(userName = "", language = AppLanguage.SYSTEM),
        )

    fun saveName(name: String) {
        viewModelScope.launch { prefs.setUserName(name.trim()) }
    }

    fun saveLanguage(language: AppLanguage) {
        viewModelScope.launch { prefs.setAppLanguage(language) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
