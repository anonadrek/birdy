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
    val archiveChip: Flow<String>
    val archiveSort: Flow<ArchiveSort>
    val lifelistSort: Flow<LifelistSort>

    /** Wall-clock epoch ms when the first install was recorded, null = not yet migrated. */
    val firstInstallTimestamp: Flow<Long?>

    /** Wall-clock epoch ms when modal was last shown, null = never shown. */
    val premiumModalLastShownAt: Flow<Long?>

    val pushPermissionAsked: Flow<Boolean>
    val dailyBirdPushEnabled: Flow<Boolean>
    val streakRiskPushEnabled: Flow<Boolean>
    val weeklyRecapPushEnabled: Flow<Boolean>

    suspend fun setUserName(name: String)

    suspend fun setHasSeenOnboarding(value: Boolean)

    suspend fun setAppLanguage(value: AppLanguage)

    suspend fun setLifelistStat3(value: LifelistStat3Choice)

    suspend fun setArchiveChip(value: String)

    suspend fun setArchiveSort(value: ArchiveSort)

    suspend fun setLifelistSort(value: LifelistSort)

    suspend fun setFirstInstallTimestamp(ms: Long)

    suspend fun setPremiumModalLastShownAt(ms: Long)

    suspend fun setPushPermissionAsked(value: Boolean)

    suspend fun setDailyBirdPushEnabled(value: Boolean)

    suspend fun setStreakRiskPushEnabled(value: Boolean)
    suspend fun setWeeklyRecapPushEnabled(value: Boolean)
}
