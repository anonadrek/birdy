package se.birdy.app.ui.badges

import se.birdy.domain.badge.BadgeCategory

/** Kurerad troférum-showcase — härledd helt ur befintlig märkesdata. */
data class TrophyShowcase(
    val hero: BadgeWithUnlock?,
    val recentlyUnlocked: List<BadgeWithUnlock>,
    val rareFinds: List<BadgeWithUnlock>,
    val closeToUnlock: List<LockedBadgeProgress>,
)

/**
 * Bygger troférummets showcase ur redan beräknade samlingar. Ren & unit-testbar —
 * inga repositories, ingen katalog-lookup.
 *
 * @param recentlyUnlocked top-5 upplåsta, DESC på unlockedAt (som i BadgesUiState.Loaded)
 * @param allUnlocked alla unlocks mappade till BadgeWithUnlock, DESC på unlockedAt
 * @param locked alla vanliga låsta märken med progress
 */
fun buildTrophyShowcase(
    recentlyUnlocked: List<BadgeWithUnlock>,
    allUnlocked: List<BadgeWithUnlock>,
    locked: List<LockedBadgeProgress>,
    maxClose: Int = 3,
): TrophyShowcase {
    val hero = recentlyUnlocked.firstOrNull()
    val recentBand = recentlyUnlocked.drop(1)
    val rareFinds =
        allUnlocked
            .filter { it.badge.category == BadgeCategory.REDLISTED }
            .sortedByDescending { it.unlockedAt }
    val closeToUnlock =
        locked
            .mapNotNull { lbp ->
                val s = lbp.state
                if (s is BadgeGridState.InProgress && s.target > 0) {
                    lbp to (s.current.toFloat() / s.target)
                } else {
                    null
                }
            }.sortedByDescending { it.second }
            .take(maxClose)
            .map { it.first }
    return TrophyShowcase(
        hero = hero,
        recentlyUnlocked = recentBand,
        rareFinds = rareFinds,
        closeToUnlock = closeToUnlock,
    )
}
