package se.birdy.app.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock

class FakeBadgeRepository : BadgeRepository {
    val unlocks = MutableStateFlow<List<BadgeUnlock>>(emptyList())

    override fun observeUnlocks(): Flow<List<BadgeUnlock>> = unlocks.asStateFlow()

    override suspend fun persist(unlocks: List<BadgeUnlock>) {
        if (unlocks.isEmpty()) return
        val byId = (this.unlocks.value + unlocks).associateBy { it.badgeId }
        this.unlocks.value = byId.values.sortedByDescending { it.unlockedAt }
    }

    override suspend fun deleteAll() {
        unlocks.value = emptyList()
    }

    fun seedUnlocks(unlocks: List<BadgeUnlock>) {
        this.unlocks.value = unlocks
    }
}
