package se.birdy.domain.badge

enum class BadgeCategory(
    val order: Int,
) {
    PROGRESSION(0),
    STREAK_WEEKLY(1),
    STREAK_MONTHLY(2),
    SEASON(3),
    FAMILY(4),
    RARE(5),
}
