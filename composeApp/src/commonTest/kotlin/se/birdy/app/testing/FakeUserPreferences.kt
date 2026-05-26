package se.birdy.app.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.ArchiveSort
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice
import se.birdy.datastore.UserPreferences

class FakeUserPreferences : UserPreferences {
    private val _userName = MutableStateFlow("")
    private val _hasSeenOnboarding = MutableStateFlow(false)
    private val _appLanguage = MutableStateFlow(AppLanguage.SYSTEM)
    private val _lifelistStat3 = MutableStateFlow(LifelistStat3Choice.STREAK)
    private val _archiveChip = MutableStateFlow("ALL")
    private val _archiveSort = MutableStateFlow(ArchiveSort.ALPHA)
    private val _lifelistSort = MutableStateFlow(LifelistSort.RECENT)
    private val _firstInstallTimestamp = MutableStateFlow<Long?>(null)
    private val _premiumModalLastShownAt = MutableStateFlow<Long?>(null)
    private val _pushPermissionAsked = MutableStateFlow(false)
    private val _dailyBirdPushEnabled = MutableStateFlow(false)
    private val _streakRiskPushEnabled = MutableStateFlow(false)

    val archiveChipWrites = mutableListOf<String>()
    var archiveSortValue: ArchiveSort
        get() = _archiveSort.value
        set(value) {
            _archiveSort.value = value
        }
    var userNameValue: String
        get() = _userName.value
        set(value) {
            _userName.value = value
        }
    var lifelistStat3Value: LifelistStat3Choice
        get() = _lifelistStat3.value
        set(value) {
            _lifelistStat3.value = value
        }
    var lifelistSortValue: LifelistSort
        get() = _lifelistSort.value
        set(value) {
            _lifelistSort.value = value
        }

    override val userName: Flow<String> = _userName.asStateFlow()
    override val hasSeenOnboarding: Flow<Boolean> = _hasSeenOnboarding.asStateFlow()
    override val appLanguage: Flow<AppLanguage> = _appLanguage.asStateFlow()
    override val lifelistStat3: Flow<LifelistStat3Choice> = _lifelistStat3.asStateFlow()
    override val archiveChip: Flow<String> = _archiveChip.asStateFlow()
    override val archiveSort: Flow<ArchiveSort> = _archiveSort.asStateFlow()
    override val lifelistSort: Flow<LifelistSort> = _lifelistSort.asStateFlow()
    override val firstInstallTimestamp: Flow<Long?> = _firstInstallTimestamp.asStateFlow()
    override val premiumModalLastShownAt: Flow<Long?> = _premiumModalLastShownAt.asStateFlow()
    override val pushPermissionAsked: Flow<Boolean> = _pushPermissionAsked.asStateFlow()
    override val dailyBirdPushEnabled: Flow<Boolean> = _dailyBirdPushEnabled.asStateFlow()
    override val streakRiskPushEnabled: Flow<Boolean> = _streakRiskPushEnabled.asStateFlow()

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
        archiveChipWrites.add(value)
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

    override suspend fun setPushPermissionAsked(value: Boolean) {
        _pushPermissionAsked.value = value
    }

    override suspend fun setDailyBirdPushEnabled(value: Boolean) {
        _dailyBirdPushEnabled.value = value
    }

    override suspend fun setStreakRiskPushEnabled(value: Boolean) {
        _streakRiskPushEnabled.value = value
    }
}
