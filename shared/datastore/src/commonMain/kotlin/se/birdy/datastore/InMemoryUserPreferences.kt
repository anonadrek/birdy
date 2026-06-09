package se.birdy.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory impl för tester. Ligger i commonMain så den är synlig från
 * composeApp:commonTest (KMP-regel: commonTest ser bara commonMain-symboler
 * från dependencies, inte jvmMain).
 */
class InMemoryUserPreferences : UserPreferences {
    private val _userName = MutableStateFlow("")
    private val _hasSeenOnboarding = MutableStateFlow(false)
    private val _appLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    private val _lifelistStat3 = MutableStateFlow(LifelistStat3Choice.STREAK)
    private val _archiveChip = MutableStateFlow("ALL")
    private val _archiveSort = MutableStateFlow(ArchiveSort.ALPHA)
    private val _lifelistSort = MutableStateFlow(LifelistSort.RECENT)
    private val _firstInstallTimestamp = MutableStateFlow<Long?>(null)
    private val _premiumModalLastShownAt = MutableStateFlow<Long?>(null)
    private val _postOnboardingPremiumShown = MutableStateFlow(false)
    private val _pushPermissionAsked = MutableStateFlow(false)
    private val _dailyBirdPushEnabled = MutableStateFlow(true)
    private val _streakRiskPushEnabled = MutableStateFlow(true)
    private val _weeklyRecapPushEnabled = MutableStateFlow(true)
    private val _locationCaptureEnabled = MutableStateFlow(false)
    private val _weeklyTrophyPushEnabled = MutableStateFlow(true)

    override val userName: Flow<String> = _userName.asStateFlow()
    override val hasSeenOnboarding: Flow<Boolean> = _hasSeenOnboarding.asStateFlow()
    override val appLanguage: Flow<AppLanguage> = _appLanguage.asStateFlow()
    override val lifelistStat3: Flow<LifelistStat3Choice> = _lifelistStat3.asStateFlow()
    override val archiveChip: Flow<String> = _archiveChip.asStateFlow()
    override val archiveSort: Flow<ArchiveSort> = _archiveSort.asStateFlow()
    override val lifelistSort: Flow<LifelistSort> = _lifelistSort.asStateFlow()
    override val firstInstallTimestamp: Flow<Long?> = _firstInstallTimestamp.asStateFlow()
    override val premiumModalLastShownAt: Flow<Long?> = _premiumModalLastShownAt.asStateFlow()
    override val postOnboardingPremiumShown: Flow<Boolean> = _postOnboardingPremiumShown.asStateFlow()
    override val pushPermissionAsked: Flow<Boolean> = _pushPermissionAsked.asStateFlow()
    override val dailyBirdPushEnabled: Flow<Boolean> = _dailyBirdPushEnabled.asStateFlow()
    override val streakRiskPushEnabled: Flow<Boolean> = _streakRiskPushEnabled.asStateFlow()
    override val weeklyRecapPushEnabled: Flow<Boolean> = _weeklyRecapPushEnabled.asStateFlow()
    override val locationCaptureEnabled: Flow<Boolean> = _locationCaptureEnabled.asStateFlow()
    override val weeklyTrophyPushEnabled: Flow<Boolean> = _weeklyTrophyPushEnabled.asStateFlow()

    override suspend fun setUserName(name: String) {
        _userName.value = name
    }

    override suspend fun setHasSeenOnboarding(value: Boolean) {
        _hasSeenOnboarding.value = value
    }

    override suspend fun setAppLanguage(value: AppLanguage) {
        _appLanguage.value = value
    }

    override suspend fun setLifelistStat3(value: LifelistStat3Choice) {
        _lifelistStat3.value = value
    }

    override suspend fun setArchiveChip(value: String) {
        _archiveChip.value = value
    }

    override suspend fun setArchiveSort(value: ArchiveSort) {
        _archiveSort.value = value
    }

    override suspend fun setLifelistSort(value: LifelistSort) {
        _lifelistSort.value = value
    }

    override suspend fun setFirstInstallTimestamp(ms: Long) {
        _firstInstallTimestamp.value = ms
    }

    override suspend fun setPremiumModalLastShownAt(ms: Long) {
        _premiumModalLastShownAt.value = ms
    }

    override suspend fun setPostOnboardingPremiumShown(value: Boolean) {
        _postOnboardingPremiumShown.value = value
    }

    override suspend fun setPushPermissionAsked(value: Boolean) {
        _pushPermissionAsked.value = value
    }

    override suspend fun setDailyBirdPushEnabled(value: Boolean) {
        _dailyBirdPushEnabled.value = value
    }

    override suspend fun setStreakRiskPushEnabled(value: Boolean) {
        _streakRiskPushEnabled.value = value
    }

    override suspend fun setWeeklyRecapPushEnabled(value: Boolean) {
        _weeklyRecapPushEnabled.value = value
    }

    override suspend fun setLocationCaptureEnabled(value: Boolean) {
        _locationCaptureEnabled.value = value
    }

    override suspend fun setWeeklyTrophyPushEnabled(value: Boolean) {
        _weeklyTrophyPushEnabled.value = value
    }
}
