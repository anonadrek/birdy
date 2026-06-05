package se.birdy.app.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.photo.PhotoStorage
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock

/** Minimal [BadgeRepository] that records nothing and returns empty unlocks. */
internal class NoopBadgeRepository : BadgeRepository {
    override fun observeUnlocks(): Flow<List<BadgeUnlock>> =
        MutableStateFlow<List<BadgeUnlock>>(emptyList()).asStateFlow()

    override suspend fun persist(unlocks: List<BadgeUnlock>) = Unit

    override suspend fun deleteAll() = Unit

    override suspend fun unlockManualBadge(
        badgeId: String,
        unlockedAt: Instant,
    ): Boolean = false
}

/** Minimal [PhotoStorage] that accepts bytes and hands back a deterministic path. */
internal class RecordingPhotoStorage : PhotoStorage {
    private var counter = 0

    override suspend fun persistJpeg(bytes: ByteArray): String = "/fake/photo-${counter++}.jpg"

    override suspend fun delete(path: String) = Unit
}

/** [Clock] that always returns the same fixed [Instant]. */
internal class FixedClock(private val fixedNow: Instant) : Clock {
    override fun now(): Instant = fixedNow
}

/** Returns a [BadgeCatalog] with no badges. */
internal fun emptyBadgeCatalog(): BadgeCatalog = BadgeCatalog(version = 1, badges = emptyList())

/** Returns a [RecalculateBadgesUseCase] bound to UTC that will never unlock anything (empty catalog). */
internal fun noopRecalculate(): RecalculateBadgesUseCase =
    RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = FixedClock(Instant.fromEpochMilliseconds(0)))
