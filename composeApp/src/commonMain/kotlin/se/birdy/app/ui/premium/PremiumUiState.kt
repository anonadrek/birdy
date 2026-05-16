package se.birdy.app.ui.premium

import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

data class PremiumUiState(
    val selectedTier: PremiumTier = PremiumTier.YEARLY,
    val purchaseInFlight: Boolean = false,
    val backendState: PremiumState = PremiumState.Free,
    val formattedYearlyPrice: String? = null,
    val formattedLifetimePrice: String? = null,
)
