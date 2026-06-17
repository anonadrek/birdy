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

    /** True när post-onboarding-premium-skärmen visats en gång (visa aldrig igen). */
    val postOnboardingPremiumShown: Flow<Boolean>

    val pushPermissionAsked: Flow<Boolean>
    val dailyBirdPushEnabled: Flow<Boolean>
    val streakRiskPushEnabled: Flow<Boolean>
    val weeklyRecapPushEnabled: Flow<Boolean>
    val locationCaptureEnabled: Flow<Boolean>
    val weeklyTrophyPushEnabled: Flow<Boolean>

    /**
     * DEBUG-only Billing-verify toggle. When true (and only in `BuildConfig.DEBUG`
     * builds), MainActivity skips the premium override so the real
     * `NotActive → purchase → Active` path is exercised even while
     * `PREMIUM_OPEN_FOR_LAUNCH=true`. Read once at app start — restart to apply.
     * Never has any effect in release builds. See billing-verify runbook §1.
     */
    val skipPremiumOverride: Flow<Boolean>

    suspend fun setUserName(name: String)

    suspend fun setHasSeenOnboarding(value: Boolean)

    suspend fun setAppLanguage(value: AppLanguage)

    suspend fun setLifelistStat3(value: LifelistStat3Choice)

    suspend fun setArchiveChip(value: String)

    suspend fun setArchiveSort(value: ArchiveSort)

    suspend fun setLifelistSort(value: LifelistSort)

    suspend fun setFirstInstallTimestamp(ms: Long)

    suspend fun setPremiumModalLastShownAt(ms: Long)

    suspend fun setPostOnboardingPremiumShown(value: Boolean)

    suspend fun setPushPermissionAsked(value: Boolean)

    suspend fun setDailyBirdPushEnabled(value: Boolean)

    suspend fun setStreakRiskPushEnabled(value: Boolean)

    suspend fun setWeeklyRecapPushEnabled(value: Boolean)

    suspend fun setLocationCaptureEnabled(value: Boolean)

    suspend fun setWeeklyTrophyPushEnabled(value: Boolean)

    suspend fun setSkipPremiumOverride(value: Boolean)
}
