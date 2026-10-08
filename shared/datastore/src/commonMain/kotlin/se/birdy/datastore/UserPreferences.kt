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

    /**
     * Wall-clock epoch ms when the first install was recorded, null = not yet migrated. Drives the
     * 7-day onboarding grace only; it follows the user-adjustable device clock, so it is never
     * early-user proof by itself (see [grandfatherLegacyCaptured]).
     */
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
     * builds), MainActivity skips every premium override — including a grandfathered
     * user's — so the real `NotActive → purchase → Active` path is exercised. Monetisation
     * has been live since 1.3.0; the only overrides left are grandfathering (spec §5.1) and
     * debug forcing. Read once at app start — restart to apply. Never has any effect in
     * release builds. See billing-verify runbook §1.
     */
    val skipPremiumOverride: Flow<Boolean>

    /** One-shot: true once the Play in-app review prompt has been requested (never ask twice). */
    val inAppReviewRequested: Flow<Boolean>

    /** One-shot: true once the early-user thank-you screen has been shown (release 1.3.0). */
    val grandfatherThanksShown: Flow<Boolean>

    /**
     * DEBUG-only QA toggle: treat this install as an early (grandfathered) user so the
     * thank-you screen can be tested. MainActivity only reads it when BuildConfig.DEBUG.
     */
    val debugForceGrandfathered: Flow<Boolean>

    /**
     * Early-user proof, part 1 (spec 2026-09-24 §5.1, hardened 2026-10-01): true once the first
     * start of a build with the hardened rule has copied [firstInstallTimestamp], exactly as an
     * older build left it, into [grandfatherLegacyInstallMs]. Captured once, also when there was
     * nothing to copy, so a timestamp written by that build or any later one never becomes proof.
     * Part of DataStore, so it travels with Google backup to a new phone.
     */
    val grandfatherLegacyCaptured: Flow<Boolean>

    /** The captured legacy install time (epoch ms); null = nothing plausible was there to capture. */
    val grandfatherLegacyInstallMs: Flow<Long?>

    /**
     * Early-user proof, part 2: the network-synchronised time (Android 13+, not adjustable in the
     * phone's settings) at the first start where the platform had one; null = none seen yet.
     * Stored once and never overwritten.
     */
    val grandfatherTrustedFirstSeenMs: Flow<Long?>

    /**
     * The local date (ISO `yyyy-MM-dd`) on which the user last opened that day's Dagens fågel
     * (release 1.3.0 Task 7d); null = never. The Identify tab shows a dot while this differs from
     * today's date, so it resets on its own when the date changes.
     */
    val dailyBirdOpenedDate: Flow<String?>

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

    suspend fun setInAppReviewRequested(value: Boolean)

    suspend fun setGrandfatherThanksShown(value: Boolean)

    suspend fun setDebugForceGrandfathered(value: Boolean)

    /**
     * The one-time legacy capture: stores [installMs] (null = nothing to keep) as
     * [grandfatherLegacyInstallMs] and sets [grandfatherLegacyCaptured], in one write wherever the
     * platform allows it, so the flag is never persisted without its value.
     */
    suspend fun captureGrandfatherLegacy(installMs: Long?)

    suspend fun setGrandfatherTrustedFirstSeenMs(ms: Long)

    suspend fun setDailyBirdOpenedDate(date: String)
}
