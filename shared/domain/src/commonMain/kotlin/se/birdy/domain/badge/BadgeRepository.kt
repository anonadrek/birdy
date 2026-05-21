package se.birdy.domain.badge

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface BadgeRepository {
    fun observeUnlocks(): Flow<List<BadgeUnlock>>

    suspend fun persist(unlocks: List<BadgeUnlock>)

    suspend fun deleteAll()

    /**
     * Idempotent manual unlock for badges that bypass the rule-engine
     * (currently: `premium_field_member`, unlocked when Premium activates).
     *
     * Returns `true` if a row was inserted (first unlock), `false` if a row
     * already existed for [badgeId] (subsequent toggles are no-ops).
     * First-write wins: the [unlockedAt] of subsequent calls is discarded.
     */
    suspend fun unlockManualBadge(badgeId: String, unlockedAt: Instant): Boolean
}
