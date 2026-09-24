package se.birdy.app.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

class FakePremiumRepository(
    initial: PremiumState = PremiumState.Free,
    private val restoreThrows: Throwable? = null,
) : PremiumRepository {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<PremiumState> = _state.asStateFlow()

    override suspend fun markPurchased(tier: PremiumTier) {
        _state.value = PremiumState.Active(tier, Clock.System.now())
    }

    override suspend fun restore() {
        restoreThrows?.let { throw it }
    }

    fun setState(state: PremiumState) {
        _state.value = state
    }
}
