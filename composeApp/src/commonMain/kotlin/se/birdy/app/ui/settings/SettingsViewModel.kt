package se.birdy.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.settings_restore_purchases_empty
import birdy_bird_scanner.composeapp.generated.resources.settings_restore_purchases_success
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import se.birdy.app.i18n.toLocaleTagOrEmpty
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

    private val _effects = Channel<SettingsEffect>(capacity = Channel.UNLIMITED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

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
            _effects.send(SettingsEffect.RestartForLocale(value.toLocaleTagOrEmpty()))
        }
    }

    fun openPrivacy() {
        viewModelScope.launch { _effects.send(SettingsEffect.OpenPrivacyUrl) }
    }

    fun openTerms() {
        viewModelScope.launch { _effects.send(SettingsEffect.OpenTermsUrl) }
    }

    fun rateOnPlayStore() {
        viewModelScope.launch { _effects.send(SettingsEffect.RateOnPlayStore) }
    }

    fun shareApp() {
        viewModelScope.launch { _effects.send(SettingsEffect.ShareApp) }
    }

    fun sendFeedback() {
        viewModelScope.launch { _effects.send(SettingsEffect.SendFeedback) }
    }

    fun openAbout() {
        viewModelScope.launch { _effects.send(SettingsEffect.OpenAbout) }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            premiumRepository.restore()
            val state = premiumRepository.state.value
            val message =
                if (state is PremiumState.Active) {
                    Res.string.settings_restore_purchases_success
                } else {
                    Res.string.settings_restore_purchases_empty
                }
            _effects.send(SettingsEffect.ShowToast(message))
        }
    }
}
