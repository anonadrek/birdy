package se.birdy.app.bootstrap

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import se.birdy.app.ui.badges.UnlockQueue
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock

private const val FIELD_MEMBER_BADGE_ID = "premium_field_member"

/**
 * Bootstrap-scoped listener that watches the Premium activation StateFlow.
 *
 * On flip to `true`, idempotently unlocks the [FIELD_MEMBER_BADGE_ID] badge via
 * [BadgeRepository.unlockManualBadge]. If the unlock is the first (returns `true`),
 * the resulting [BadgeUnlock] is enqueued in [UnlockQueue] so the welcome bottom-sheet
 * fires exactly once across the app's lifetime.
 *
 * Lifecycle is owned by the caller — [start] returns a [Job] that should be cancelled
 * when the host scope (e.g. `lifecycleScope` in MainActivity) is torn down.
 */
class PremiumActivationListener(
    private val premiumActiveFlow: StateFlow<Boolean>,
    private val badgeRepo: BadgeRepository,
    private val unlockQueue: UnlockQueue,
    private val clock: Clock = Clock.System,
) {
    fun start(scope: CoroutineScope): Job =
        premiumActiveFlow
            .filter { it }
            .onEach {
                val now = clock.now()
                val firstUnlock = badgeRepo.unlockManualBadge(FIELD_MEMBER_BADGE_ID, now)
                if (firstUnlock) {
                    unlockQueue.enqueue(listOf(BadgeUnlock(FIELD_MEMBER_BADGE_ID, now)))
                }
            }.launchIn(scope)
}
