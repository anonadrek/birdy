package se.birdy.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/**
 * iOS actual backing store for [UserPreferences]. Mirrors [AndroidUserPreferences]
 * (DataStore) exactly — same 18 keys and same default values — but persists via
 * NSUserDefaults instead of DataStore. Each property is a MutableStateFlow seeded
 * from NSUserDefaults at construction and updated on every setter, so Flow consumers
 * react to changes exactly as on Android while the value survives app relaunch.
 *
 * Constructed once per process in the iOS AppGraph, so the in-memory flows are always
 * consistent with the persisted values (this instance is the only writer).
 */
internal class NsUserDefaultsUserPreferences(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : UserPreferences {
    private object Keys {
        const val USER_NAME = "user_name"
        const val HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        const val APP_LANGUAGE = "app_language"
        const val LIFELIST_STAT3 = "lifelist_stat3_choice"
        const val ARCHIVE_CHIP = "archive_chip"
        const val ARCHIVE_SORT = "archive_sort"
        const val LIFELIST_SORT = "lifelist_sort"
        const val FIRST_INSTALL_TIMESTAMP = "first_install_timestamp"
        const val PREMIUM_MODAL_LAST_SHOWN_AT = "premium_modal_last_shown_at_ms"
        const val POST_ONBOARDING_PREMIUM_SHOWN = "post_onboarding_premium_shown"
        const val PUSH_PERMISSION_ASKED = "push_permission_asked"
        const val DAILY_BIRD_PUSH_ENABLED = "daily_bird_push_enabled"
        const val STREAK_RISK_PUSH_ENABLED = "streak_risk_push_enabled"
        const val WEEKLY_RECAP_PUSH_ENABLED = "weekly_recap_push_enabled"
        const val LOCATION_CAPTURE_ENABLED = "location_capture_enabled"
        const val WEEKLY_TROPHY_PUSH_ENABLED = "weekly_trophy_push_enabled"
        const val SKIP_PREMIUM_OVERRIDE = "skip_premium_override"
        const val IN_APP_REVIEW_REQUESTED = "in_app_review_requested"
    }

    // ---- NSUserDefaults primitives ----
    private fun getString(
        key: String,
        default: String,
    ): String = defaults.stringForKey(key) ?: default

    private fun putString(
        key: String,
        value: String,
    ) = defaults.setObject(value, forKey = key)

    // objectForKey==null distinguishes "unset" from "explicitly false", so true-default keys work.
    private fun getBool(
        key: String,
        default: Boolean,
    ): Boolean = if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    private fun putBool(
        key: String,
        value: Boolean,
    ) = defaults.setBool(value, forKey = key)

    // Nullable Longs are encoded as strings to keep "unset" == null (NSNumber can't express null).
    private fun getLongOrNull(key: String): Long? = defaults.stringForKey(key)?.toLongOrNull()

    private fun putLong(
        key: String,
        value: Long,
    ) = defaults.setObject(value.toString(), forKey = key)

    // ---- Backing flows, seeded from the persisted values ----
    private val _userName = MutableStateFlow(getString(Keys.USER_NAME, ""))
    private val _hasSeenOnboarding = MutableStateFlow(getBool(Keys.HAS_SEEN_ONBOARDING, false))
    private val _appLanguage =
        MutableStateFlow(
            AppLanguage.entries.firstOrNull { it.name == defaults.stringForKey(Keys.APP_LANGUAGE) }
                ?: AppLanguage.SYSTEM,
        )
    private val _lifelistStat3 =
        MutableStateFlow(
            LifelistStat3Choice.entries.firstOrNull { it.name == defaults.stringForKey(Keys.LIFELIST_STAT3) }
                ?: LifelistStat3Choice.STREAK,
        )
    private val _archiveChip = MutableStateFlow(getString(Keys.ARCHIVE_CHIP, "ALL"))
    private val _archiveSort =
        MutableStateFlow(
            ArchiveSort.entries.firstOrNull { it.name == defaults.stringForKey(Keys.ARCHIVE_SORT) }
                ?: ArchiveSort.ALPHA,
        )
    private val _lifelistSort =
        MutableStateFlow(
            LifelistSort.entries.firstOrNull { it.name == defaults.stringForKey(Keys.LIFELIST_SORT) }
                ?: LifelistSort.RECENT,
        )
    private val _firstInstallTimestamp = MutableStateFlow(getLongOrNull(Keys.FIRST_INSTALL_TIMESTAMP))
    private val _premiumModalLastShownAt = MutableStateFlow(getLongOrNull(Keys.PREMIUM_MODAL_LAST_SHOWN_AT))
    private val _postOnboardingPremiumShown = MutableStateFlow(getBool(Keys.POST_ONBOARDING_PREMIUM_SHOWN, false))
    private val _pushPermissionAsked = MutableStateFlow(getBool(Keys.PUSH_PERMISSION_ASKED, false))
    private val _dailyBirdPushEnabled = MutableStateFlow(getBool(Keys.DAILY_BIRD_PUSH_ENABLED, true))
    private val _streakRiskPushEnabled = MutableStateFlow(getBool(Keys.STREAK_RISK_PUSH_ENABLED, true))
    private val _weeklyRecapPushEnabled = MutableStateFlow(getBool(Keys.WEEKLY_RECAP_PUSH_ENABLED, true))
    private val _locationCaptureEnabled = MutableStateFlow(getBool(Keys.LOCATION_CAPTURE_ENABLED, false))
    private val _weeklyTrophyPushEnabled = MutableStateFlow(getBool(Keys.WEEKLY_TROPHY_PUSH_ENABLED, true))
    private val _skipPremiumOverride = MutableStateFlow(getBool(Keys.SKIP_PREMIUM_OVERRIDE, false))
    private val _inAppReviewRequested = MutableStateFlow(getBool(Keys.IN_APP_REVIEW_REQUESTED, false))

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
    override val skipPremiumOverride: Flow<Boolean> = _skipPremiumOverride.asStateFlow()
    override val inAppReviewRequested: Flow<Boolean> = _inAppReviewRequested.asStateFlow()

    override suspend fun setUserName(name: String) {
        putString(Keys.USER_NAME, name)
        _userName.value = name
    }

    override suspend fun setHasSeenOnboarding(value: Boolean) {
        putBool(Keys.HAS_SEEN_ONBOARDING, value)
        _hasSeenOnboarding.value = value
    }

    override suspend fun setAppLanguage(value: AppLanguage) {
        putString(Keys.APP_LANGUAGE, value.name)
        _appLanguage.value = value
    }

    override suspend fun setLifelistStat3(value: LifelistStat3Choice) {
        putString(Keys.LIFELIST_STAT3, value.name)
        _lifelistStat3.value = value
    }

    override suspend fun setArchiveChip(value: String) {
        putString(Keys.ARCHIVE_CHIP, value)
        _archiveChip.value = value
    }

    override suspend fun setArchiveSort(value: ArchiveSort) {
        putString(Keys.ARCHIVE_SORT, value.name)
        _archiveSort.value = value
    }

    override suspend fun setLifelistSort(value: LifelistSort) {
        putString(Keys.LIFELIST_SORT, value.name)
        _lifelistSort.value = value
    }

    override suspend fun setFirstInstallTimestamp(ms: Long) {
        putLong(Keys.FIRST_INSTALL_TIMESTAMP, ms)
        _firstInstallTimestamp.value = ms
    }

    override suspend fun setPremiumModalLastShownAt(ms: Long) {
        putLong(Keys.PREMIUM_MODAL_LAST_SHOWN_AT, ms)
        _premiumModalLastShownAt.value = ms
    }

    override suspend fun setPostOnboardingPremiumShown(value: Boolean) {
        putBool(Keys.POST_ONBOARDING_PREMIUM_SHOWN, value)
        _postOnboardingPremiumShown.value = value
    }

    override suspend fun setPushPermissionAsked(value: Boolean) {
        putBool(Keys.PUSH_PERMISSION_ASKED, value)
        _pushPermissionAsked.value = value
    }

    override suspend fun setDailyBirdPushEnabled(value: Boolean) {
        putBool(Keys.DAILY_BIRD_PUSH_ENABLED, value)
        _dailyBirdPushEnabled.value = value
    }

    override suspend fun setStreakRiskPushEnabled(value: Boolean) {
        putBool(Keys.STREAK_RISK_PUSH_ENABLED, value)
        _streakRiskPushEnabled.value = value
    }

    override suspend fun setWeeklyRecapPushEnabled(value: Boolean) {
        putBool(Keys.WEEKLY_RECAP_PUSH_ENABLED, value)
        _weeklyRecapPushEnabled.value = value
    }

    override suspend fun setLocationCaptureEnabled(value: Boolean) {
        putBool(Keys.LOCATION_CAPTURE_ENABLED, value)
        _locationCaptureEnabled.value = value
    }

    override suspend fun setWeeklyTrophyPushEnabled(value: Boolean) {
        putBool(Keys.WEEKLY_TROPHY_PUSH_ENABLED, value)
        _weeklyTrophyPushEnabled.value = value
    }

    override suspend fun setSkipPremiumOverride(value: Boolean) {
        putBool(Keys.SKIP_PREMIUM_OVERRIDE, value)
        _skipPremiumOverride.value = value
    }

    override suspend fun setInAppReviewRequested(value: Boolean) {
        putBool(Keys.IN_APP_REVIEW_REQUESTED, value)
        _inAppReviewRequested.value = value
    }
}
