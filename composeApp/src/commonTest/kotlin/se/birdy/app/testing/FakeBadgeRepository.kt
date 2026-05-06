package se.birdy.app.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock

class FakeBadgeRepository : BadgeRepository {
    private val _unlocks = MutableStateFlow<List<BadgeUnlock>>(emptyList())
    val unlocks: Flow<List<BadgeUnlock>> get() = _unlocks.asStateFlow()

    override fun observeUnlocks(): Flow<List<BadgeUnlock>> = _unlocks.asStateFlow()

    override suspend fun persist(unlocks: List<BadgeUnlock>) {
        if (unlocks.isEmpty()) return
        val byId = (_unlocks.value + unlocks).associateBy { it.badgeId }
        _unlocks.value = byId.values.sortedByDescending { it.unlockedAt }
    }

    override suspend fun deleteAll() {
        _unlocks.value = emptyList()
    }

    fun seedUnlocks(unlocks: List<BadgeUnlock>) {
        _unlocks.value = unlocks
    }
}
