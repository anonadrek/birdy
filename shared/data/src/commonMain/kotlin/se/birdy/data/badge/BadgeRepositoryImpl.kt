package se.birdy.data.badge

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import se.birdy.data.db.BadgeUnlockQueries
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.data.db.Badge_unlock as DbBadgeUnlock

class BadgeRepositoryImpl(
    private val queries: BadgeUnlockQueries,
) : BadgeRepository {

    private val manualUnlockMutex = Mutex()

    override fun observeUnlocks(): Flow<List<BadgeUnlock>> =
        queries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun persist(unlocks: List<BadgeUnlock>) {
        if (unlocks.isEmpty()) return
        queries.transaction {
            unlocks.forEach { u ->
                queries.upsert(
                    badge_id = u.badgeId,
                    unlocked_at_ms = u.unlockedAt.toEpochMilliseconds(),
                )
            }
        }
    }

    override suspend fun deleteAll() {
        queries.deleteAll()
    }

    override suspend fun unlockManualBadge(badgeId: String, unlockedAt: Instant): Boolean =
        manualUnlockMutex.withLock {
            val existing = queries.selectOne(badgeId).executeAsOneOrNull()
            if (existing != null) return@withLock false
            queries.insertIfMissing(badgeId, unlockedAt.toEpochMilliseconds())
            true
        }

    private fun toDomain(row: DbBadgeUnlock): BadgeUnlock =
        BadgeUnlock(
            badgeId = row.badge_id,
            unlockedAt = Instant.fromEpochMilliseconds(row.unlocked_at_ms),
        )
}
