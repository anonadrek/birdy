package se.birdy.domain.badge

enum class BadgeTier { MILESTONE, HABIT }

enum class BadgeCategory(
    val order: Int,
    val tier: BadgeTier,
) {
    PROGRESSION(0, BadgeTier.MILESTONE),
    FAMILY(1, BadgeTier.MILESTONE),
    BREADTH(2, BadgeTier.MILESTONE),
    REDLISTED(3, BadgeTier.MILESTONE),
    SEASON(4, BadgeTier.MILESTONE),
    AUDIO(5, BadgeTier.MILESTONE),
    STREAK_WEEKLY(6, BadgeTier.HABIT),
    STREAK_MONTHLY(7, BadgeTier.HABIT),
}
