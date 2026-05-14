package se.birdy.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.UserPreferences
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState

class SettingsViewModel(
    private val prefs: UserPreferences,
    private val premiumRepository: PremiumRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>(replay = 1, extraBufferCapacity = 1)
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                prefs.userName,
                prefs.appLanguage,
                premiumRepository.state,
            ) { name, lang, premium ->
                SettingsUiState(
                    userName = name,
                    language = lang,
                    premiumActive = premium !is PremiumState.Free,
                )
            }.collect { _state.value = it }
        }
    }

    fun saveName(name: String) {
        viewModelScope.launch { prefs.setUserName(name.trim()) }
    }

    fun saveLanguage(value: AppLanguage) {
        viewModelScope.launch {
            prefs.setAppLanguage(value)
            val tag =
                when (value) {
                    AppLanguage.SV -> "sv"
                    AppLanguage.EN -> "en"
                    AppLanguage.SYSTEM -> ""
                }
            _effects.tryEmit(SettingsEffect.RestartForLocale(tag))
        }
    }
}
