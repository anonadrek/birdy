package se.birdy.domain.badge

import kotlinx.datetime.Instant

data class BadgeUnlock(
    val badgeId: String,
    val unlockedAt: Instant,
)
