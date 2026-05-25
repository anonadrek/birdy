package se.birdy.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "birdy_user_prefs")

actual class UserPreferencesStore actual constructor(
    platformContext: Any?,
) {
    private val context: Context =
        (platformContext as? Context)
            ?: error("Android UserPreferencesStore requires Context, got: $platformContext")

    actual fun preferences(): UserPreferences = AndroidUserPreferences(context.userPrefsDataStore)
}

private class AndroidUserPreferences(
    private val store: DataStore<Preferences>,
) : UserPreferences {
    private val safeData: Flow<Preferences> =
        store.data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val LIFELIST_STAT3 = stringPreferencesKey("lifelist_stat3_choice")
        val ARCHIVE_CHIP = stringPreferencesKey("archive_chip")
        val ARCHIVE_SORT = stringPreferencesKey("archive_sort")
        val LIFELIST_SORT = stringPreferencesKey("lifelist_sort")
        val FIRST_INSTALL_TIMESTAMP = longPreferencesKey("first_install_timestamp")
        val PREMIUM_MODAL_LAST_SHOWN_AT = longPreferencesKey("premium_modal_last_shown_at_ms")
        val PUSH_PERMISSION_ASKED = booleanPreferencesKey("push_permission_asked")
        val DAILY_BIRD_PUSH_ENABLED = booleanPreferencesKey("daily_bird_push_enabled")
        val STREAK_RISK_PUSH_ENABLED = booleanPreferencesKey("streak_risk_push_enabled")
    }

    override val userName: Flow<String> = safeData.map { it[Keys.USER_NAME] ?: "" }
    override val hasSeenOnboarding: Flow<Boolean> = safeData.map { it[Keys.HAS_SEEN_ONBOARDING] ?: false }

    override val appLanguage: Flow<AppLanguage> =
        safeData.map { prefs ->
            AppLanguage.entries.firstOrNull { it.name == prefs[Keys.APP_LANGUAGE] } ?: AppLanguage.SYSTEM
        }
    override val lifelistStat3: Flow<LifelistStat3Choice> =
        safeData.map { prefs ->
            LifelistStat3Choice.entries.firstOrNull { it.name == prefs[Keys.LIFELIST_STAT3] }
                ?: LifelistStat3Choice.STREAK
        }
    override val archiveChip: Flow<String> = safeData.map { it[Keys.ARCHIVE_CHIP] ?: "ALL" }
    override val archiveSort: Flow<ArchiveSort> =
        safeData.map { prefs ->
            ArchiveSort.entries.firstOrNull { it.name == prefs[Keys.ARCHIVE_SORT] } ?: ArchiveSort.ALPHA
        }
    override val lifelistSort: Flow<LifelistSort> =
        safeData.map { prefs ->
            LifelistSort.entries.firstOrNull { it.name == prefs[Keys.LIFELIST_SORT] } ?: LifelistSort.RECENT
        }
    override val firstInstallTimestamp: Flow<Long?> =
        safeData.map { it[Keys.FIRST_INSTALL_TIMESTAMP]?.takeIf { ms -> ms > 0L } }
    override val premiumModalLastShownAt: Flow<Long?> =
        safeData.map { it[Keys.PREMIUM_MODAL_LAST_SHOWN_AT]?.takeIf { ms -> ms > 0L } }
    override val pushPermissionAsked: Flow<Boolean> =
        safeData.map { it[Keys.PUSH_PERMISSION_ASKED] ?: false }
    override val dailyBirdPushEnabled: Flow<Boolean> =
        safeData.map { it[Keys.DAILY_BIRD_PUSH_ENABLED] ?: true }
    override val streakRiskPushEnabled: Flow<Boolean> =
        safeData.map { it[Keys.STREAK_RISK_PUSH_ENABLED] ?: true }

    override suspend fun setUserName(name: String) {
        store.edit { it[Keys.USER_NAME] = name }
    }

    override suspend fun setHasSeenOnboarding(value: Boolean) {
        store.edit { it[Keys.HAS_SEEN_ONBOARDING] = value }
    }

    override suspend fun setAppLanguage(value: AppLanguage) {
        store.edit { it[Keys.APP_LANGUAGE] = value.name }
    }

    override suspend fun setLifelistStat3(value: LifelistStat3Choice) {
        store.edit { it[Keys.LIFELIST_STAT3] = value.name }
    }

    override suspend fun setArchiveChip(value: String) {
        store.edit { it[Keys.ARCHIVE_CHIP] = value }
    }

    override suspend fun setArchiveSort(value: ArchiveSort) {
        store.edit { it[Keys.ARCHIVE_SORT] = value.name }
    }

    override suspend fun setLifelistSort(value: LifelistSort) {
        store.edit { it[Keys.LIFELIST_SORT] = value.name }
    }

    override suspend fun setFirstInstallTimestamp(ms: Long) {
        store.edit { it[Keys.FIRST_INSTALL_TIMESTAMP] = ms }
    }

    override suspend fun setPremiumModalLastShownAt(ms: Long) {
        store.edit { it[Keys.PREMIUM_MODAL_LAST_SHOWN_AT] = ms }
    }

    override suspend fun setPushPermissionAsked(value: Boolean) {
        store.edit { it[Keys.PUSH_PERMISSION_ASKED] = value }
    }

    override suspend fun setDailyBirdPushEnabled(value: Boolean) {
        store.edit { it[Keys.DAILY_BIRD_PUSH_ENABLED] = value }
    }

    override suspend fun setStreakRiskPushEnabled(value: Boolean) {
        store.edit { it[Keys.STREAK_RISK_PUSH_ENABLED] = value }
    }
}
