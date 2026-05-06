package se.birdy.app.usecase

import se.birdy.domain.badge.BadgeUnlock

data class SaveResult(
    val observationId: String,
    val newUnlocks: List<BadgeUnlock>,
)
