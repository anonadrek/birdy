package se.birdy.domain.badge

data class BadgeProgress(
    val badge: Badge,
    val current: Int,
    val target: Int,
    val unlock: BadgeUnlock?,
) {
    val isUnlocked: Boolean get() = unlock != null
    val progressFraction: Float get() = (current.toFloat() / target).coerceAtMost(1f)
}
