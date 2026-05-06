package se.birdy.domain.badge

import kotlinx.coroutines.flow.Flow

interface BadgeRepository {
    fun observeUnlocks(): Flow<List<BadgeUnlock>>

    suspend fun persist(unlocks: List<BadgeUnlock>)

    suspend fun deleteAll()
}
