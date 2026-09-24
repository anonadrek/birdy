package se.birdy.app.ui.premium

import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

data class PremiumUiState(
    val selectedTier: PremiumTier = PremiumTier.YEARLY,
    val purchaseInFlight: Boolean = false,
    val backendState: PremiumState = PremiumState.Free,
    val formattedYearlyPrice: String? = null,
    val formattedLifetimePrice: String? = null,
    /** A purchase was launched from this screen and we're waiting for Play to confirm it. */
    val awaitingActivation: Boolean = false,
    /** Play confirmed the purchase (backend flipped to Active after [awaitingActivation]). */
    val purchaseCompleted: Boolean = false,
)
