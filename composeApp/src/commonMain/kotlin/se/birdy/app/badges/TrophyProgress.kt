package se.birdy.app.badges

/** One badge's progress toward unlocking. */
data class BadgeProgressItem(
    val badgeId: String,
    val current: Int,
    val target: Int,
    val unlocked: Boolean,
)

/** Weekly trophy-progression summary for the notification. */
data class TrophyProgressSummary(
    val unlockedCount: Int,
    val totalCount: Int,
    val closest: BadgeProgressItem?,
)

object TrophyProgress {
    /**
     * Summarise badge progress and pick the locked, in-progress badge closest to unlocking
     * (smallest remaining; ties broken by higher completion ratio). Untouched (current == 0)
     * and already-unlocked badges are not candidates.
     */
    fun summarize(items: List<BadgeProgressItem>): TrophyProgressSummary {
        val closest =
            items
                .filter { !it.unlocked && it.current in 1 until it.target }
                .minWithOrNull(compareBy({ it.target - it.current }, { -(it.current.toDouble() / it.target) }))
        return TrophyProgressSummary(
            unlockedCount = items.count { it.unlocked },
            totalCount = items.size,
            closest = closest,
        )
    }
}
