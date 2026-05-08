package se.birdy.datastore

import kotlinx.coroutines.flow.Flow

enum class AppLanguage { SV, EN, SYSTEM }

enum class LifelistStat3Choice {
    STREAK,
    SPECIES_THIS_YEAR,
    SPECIES_THIS_MONTH,
    LONGEST_STREAK,
}

enum class ArchiveSort { ALPHA, FAMILY, RECENT }

enum class LifelistSort { RECENT, STAMP_NUMBER, SPECIES }

interface UserPreferences {
    val userName: Flow<String>
    val hasSeenOnboarding: Flow<Boolean>
    val appLanguage: Flow<AppLanguage>
    val lifelistStat3: Flow<LifelistStat3Choice>
    val archiveSort: Flow<ArchiveSort>
    val lifelistSort: Flow<LifelistSort>

    suspend fun setUserName(name: String)

    suspend fun setHasSeenOnboarding(value: Boolean)

    suspend fun setAppLanguage(value: AppLanguage)

    suspend fun setLifelistStat3(value: LifelistStat3Choice)

    suspend fun setArchiveSort(value: ArchiveSort)

    suspend fun setLifelistSort(value: LifelistSort)
}
