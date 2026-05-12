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
    private val _premiumModalLastShown = MutableStateFlow<String?>(null)

    override val userName: Flow<String> = _userName.asStateFlow()
    override val hasSeenOnboarding: Flow<Boolean> = _hasSeenOnboarding.asStateFlow()
    override val appLanguage: Flow<AppLanguage> = _appLanguage.asStateFlow()
    override val lifelistStat3: Flow<LifelistStat3Choice> = _lifelistStat3.asStateFlow()
    override val archiveChip: Flow<String> = _archiveChip.asStateFlow()
    override val archiveSort: Flow<ArchiveSort> = _archiveSort.asStateFlow()
    override val lifelistSort: Flow<LifelistSort> = _lifelistSort.asStateFlow()
    override val premiumModalLastShown: Flow<String?> = _premiumModalLastShown.asStateFlow()

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

    override suspend fun setPremiumModalLastShown(isoDate: String) {
        _premiumModalLastShown.value = isoDate
    }
}
