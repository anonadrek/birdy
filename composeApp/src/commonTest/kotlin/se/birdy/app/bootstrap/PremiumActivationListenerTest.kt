package se.birdy.app.bootstrap

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import se.birdy.app.ui.badges.UnlockQueue
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock
import kotlin.test.Test
import kotlin.test.assertEquals

class PremiumActivationListenerTest {
    @Test
    fun flip_to_active_unlocks_field_member_exactly_once() =
        runTest {
            val premiumActive = MutableStateFlow(false)
            val repo = FakeBadgeRepository()
            val queue = UnlockQueue()
            val listener =
                PremiumActivationListener(
                    premiumActiveFlow = premiumActive,
                    badgeRepo = repo,
                    unlockQueue = queue,
                    clock = Clock.System,
                )
            val job = listener.start(this)

            // Initial false — no unlock
            advanceUntilIdle()
            assertEquals(0, repo.manualCalls.size)

            // First flip to true → unlocks
            premiumActive.value = true
            advanceUntilIdle()
            assertEquals(1, repo.manualCalls.size)
            assertEquals("premium_field_member", repo.manualCalls.single().badgeId)
            assertEquals(listOf("premium_field_member"), queue.queue.value.map { it.badgeId })

            // Toggle off then on again — no extra unlock (repo returns false; queue stays at size 1)
            premiumActive.value = false
            advanceUntilIdle()
            premiumActive.value = true
            advanceUntilIdle()
            assertEquals(2, repo.manualCalls.size, "repo called both times; second returns false")
            assertEquals(1, queue.queue.value.size, "queue only got one item (first unlock)")

            job.cancel()
        }
}

/** In-memory fake: first call returns true, subsequent calls for same id return false. */
private class FakeBadgeRepository : BadgeRepository {
    data class ManualCall(val badgeId: String, val at: Instant)

    val manualCalls = mutableListOf<ManualCall>()
    private val unlocked = mutableSetOf<String>()

    override fun observeUnlocks() = kotlinx.coroutines.flow.flowOf(emptyList<BadgeUnlock>())

    override suspend fun persist(unlocks: List<BadgeUnlock>) {}

    override suspend fun deleteAll() {
        unlocked.clear()
    }

    override suspend fun unlockManualBadge(
        badgeId: String,
        unlockedAt: Instant,
    ): Boolean {
        manualCalls += ManualCall(badgeId, unlockedAt)
        return unlocked.add(badgeId)
    }
}
