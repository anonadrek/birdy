package se.birdy.datastore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import se.birdy.domain.premium.DebugPremiumOverrides
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

actual class PremiumStateStore actual constructor(
    platformContext: Any?,
) {
    private val repo = InMemoryPremiumRepository()

    actual fun repository(): PremiumRepository = repo

    actual fun debugOverrides(): DebugPremiumOverrides = repo
}

private class InMemoryPremiumRepository :
    PremiumRepository,
    DebugPremiumOverrides {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    override val state: StateFlow<PremiumState> = _state.asStateFlow()

    override suspend fun markPurchased(tier: PremiumTier) {
        mutex.withLock {
            _state.value = PremiumState.Active(tier, Clock.System.now())
        }
    }

    override suspend fun restore() {
        // no-op for in-memory
    }

    override suspend fun forceState(state: PremiumState) {
        mutex.withLock { _state.value = state }
    }
}
