package se.birdy.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.settings_restore_purchases_empty
import birdy_bird_scanner.composeapp.generated.resources.settings_restore_purchases_success
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.birdy.app.i18n.toLocaleTagOrEmpty
import se.birdy.app.notifications.PlatformNotificationsApi
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.UserPreferences
import se.birdy.domain.notification.NotificationScheduler
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState

class SettingsViewModel(
    private val prefs: UserPreferences,
    private val premiumRepository: PremiumRepository,
    private val notificationScheduler: NotificationScheduler? = null,
    private val platformNotificationsApi: PlatformNotificationsApi? = null,
    private val devTriggerDailyBird: (() -> Unit)? = null,
    private val devTriggerWeeklyRecap: (() -> Unit)? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = Channel<SettingsEffect>(capacity = Channel.UNLIMITED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    val dailyBirdPushEnabled: StateFlow<Boolean> =
        prefs.dailyBirdPushEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val weeklyRecapPushEnabled: StateFlow<Boolean> =
        prefs.weeklyRecapPushEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val locationCaptureEnabled: StateFlow<Boolean> =
        prefs.locationCaptureEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    fun openWebsite() {
        viewModelScope.launch { _effects.send(SettingsEffect.OpenWebsiteUrl) }
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

    fun setDailyBirdPushEnabled(value: Boolean) {
        viewModelScope.launch {
            prefs.setDailyBirdPushEnabled(value)
            if (value) notificationScheduler?.scheduleDailyBird() else notificationScheduler?.cancelDailyBird()
        }
    }

    fun setWeeklyRecapPushEnabled(value: Boolean) {
        viewModelScope.launch {
            prefs.setWeeklyRecapPushEnabled(value)
            if (value) notificationScheduler?.scheduleWeeklyRecap() else notificationScheduler?.cancelWeeklyRecap()
        }
    }

    fun setLocationCaptureEnabled(value: Boolean) {
        viewModelScope.launch { prefs.setLocationCaptureEnabled(value) }
    }

    fun areNotificationsEnabled(): Boolean = platformNotificationsApi?.areNotificationsEnabled() ?: true

    fun openAppNotificationSettings() {
        platformNotificationsApi?.openAppNotificationSettings()
    }

    val devToolsAvailable: Boolean
        get() = devTriggerDailyBird != null || devTriggerWeeklyRecap != null

    fun devTriggerDailyBirdPush() {
        devTriggerDailyBird?.invoke()
    }

    fun devTriggerWeeklyRecapPush() {
        devTriggerWeeklyRecap?.invoke()
    }

    private var restoreInFlight = false

    fun restorePurchases() {
        if (restoreInFlight) return
        restoreInFlight = true
        viewModelScope.launch {
            try {
                runCatching { premiumRepository.restore() }
                    .onFailure { /* swallow; state.value still reflects last-known premium status */ }
                val currentPremium = premiumRepository.state.value
                val message =
                    if (currentPremium is PremiumState.Active) {
                        Res.string.settings_restore_purchases_success
                    } else {
                        Res.string.settings_restore_purchases_empty
                    }
                _effects.send(SettingsEffect.ShowToast(message))
            } finally {
                restoreInFlight = false
            }
        }
    }
}
