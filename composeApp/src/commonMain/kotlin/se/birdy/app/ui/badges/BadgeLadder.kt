package se.birdy.app.ui.badges

import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeRule

/** A pointer to the next un-earned tier in the same milestone ladder. */
data class NextTier(
    val nextBadgeId: String,
    val remaining: Int,
)

object BadgeLadder {
    /** Identity grouping badges into one ladder; null = not a ladder (one-off/binary/habit). */
    private fun ladderKey(rule: BadgeRule): String? =
        when (rule) {
            is BadgeRule.CountUniqueSpecies -> "unique"
            is BadgeRule.CountDistinctFamilies -> "families"
            is BadgeRule.CountDistinctOrders -> "orders"
            is BadgeRule.ObservedRedListed -> "redlisted"
            is BadgeRule.ObservedInFamily -> "family:${rule.family}"
            is BadgeRule.ObservedInFamilyGroup -> "familygroup:${rule.families.sorted().joinToString(",")}"
            else -> null
        }

    /**
     * Next un-earned tier in [unlocked]'s ladder among [locked], or null if [unlocked] is the
     * top tier / not laddered. remaining = next target − current progress (≥ 0).
     */
    fun nextTier(unlocked: Badge, locked: List<LockedBadgeProgress>): NextTier? {
        val key = ladderKey(unlocked.rule) ?: return null
        val next =
            locked
                .filter { ladderKey(it.badge.rule) == key && it.badge.rule.target > unlocked.rule.target }
                .minByOrNull { it.badge.rule.target } ?: return null
        val current = (next.state as? BadgeGridState.InProgress)?.current ?: 0
        return NextTier(
            nextBadgeId = next.badge.id,
            remaining = (next.badge.rule.target - current).coerceAtLeast(0),
        )
    }
}
