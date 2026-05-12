package se.birdy.domain.premium

import kotlinx.datetime.Instant

enum class PremiumTier { YEARLY, LIFETIME }

sealed interface PremiumState {
    data object Free : PremiumState

    data class Active(
        val tier: PremiumTier,
        val purchasedAt: Instant,
    ) : PremiumState
}
