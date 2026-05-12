package se.birdy.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import se.birdy.domain.premium.DebugPremiumOverrides
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import java.io.IOException

private val Context.premiumDataStore: DataStore<Preferences> by preferencesDataStore(name = "birdy_premium")

actual class PremiumStateStore actual constructor(
    platformContext: Any?,
) {
    private val context: Context =
        (platformContext as? Context)
            ?: error("Android PremiumStateStore requires Context, got: $platformContext")

    private val repo = AndroidPremiumRepository(context.premiumDataStore)

    actual fun repository(): PremiumRepository = repo

    actual fun debugOverrides(): DebugPremiumOverrides = repo
}

private class AndroidPremiumRepository(
    private val store: DataStore<Preferences>,
) : PremiumRepository,
    DebugPremiumOverrides {
    private object Keys {
        val PREMIUM_STATE = stringPreferencesKey("premium_state")
        val PREMIUM_PURCHASED_AT = longPreferencesKey("premium_purchased_at")
    }

    private val safeData =
        store.data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    override val state: StateFlow<PremiumState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Seed initial value synchronously so first emission isn't lost.
        runBlocking { _state.value = readPremiumState() }
        scope.launch {
            safeData.map { prefs -> mapPrefs(prefs) }.collect { _state.value = it }
        }
    }

    private suspend fun readPremiumState(): PremiumState = mapPrefs(safeData.first())

    private fun mapPrefs(prefs: Preferences): PremiumState {
        val raw = prefs[Keys.PREMIUM_STATE] ?: "FREE"
        val purchasedAt = prefs[Keys.PREMIUM_PURCHASED_AT] ?: 0L
        return when (raw) {
            "YEARLY" ->
                if (purchasedAt > 0) {
                    PremiumState.Active(PremiumTier.YEARLY, Instant.fromEpochMilliseconds(purchasedAt))
                } else {
                    PremiumState.Free
                }
            "LIFETIME" ->
                if (purchasedAt > 0) {
                    PremiumState.Active(PremiumTier.LIFETIME, Instant.fromEpochMilliseconds(purchasedAt))
                } else {
                    PremiumState.Free
                }
            else -> PremiumState.Free
        }
    }

    override suspend fun markPurchased(tier: PremiumTier) {
        val now = Clock.System.now()
        store.edit { prefs ->
            prefs[Keys.PREMIUM_STATE] = tier.name
            prefs[Keys.PREMIUM_PURCHASED_AT] = now.toEpochMilliseconds()
        }
    }

    override suspend fun restore() {
        _state.value = readPremiumState()
    }

    override suspend fun forceState(state: PremiumState) {
        when (state) {
            PremiumState.Free ->
                store.edit { prefs ->
                    prefs[Keys.PREMIUM_STATE] = "FREE"
                    prefs[Keys.PREMIUM_PURCHASED_AT] = 0L
                }
            is PremiumState.Active ->
                store.edit { prefs ->
                    prefs[Keys.PREMIUM_STATE] = state.tier.name
                    prefs[Keys.PREMIUM_PURCHASED_AT] = state.purchasedAt.toEpochMilliseconds()
                }
        }
    }
}
