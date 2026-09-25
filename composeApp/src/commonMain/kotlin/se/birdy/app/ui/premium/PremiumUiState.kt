package se.birdy.app.ui.premium

import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

data class PremiumUiState(
    val selectedTier: PremiumTier = PremiumTier.YEARLY,
    val purchaseInFlight: Boolean = false,
    val backendState: PremiumState = PremiumState.Free,
    val formattedYearlyPrice: String? = null,
    val formattedLifetimePrice: String? = null,
    /**
     * A purchase was launched from this screen and we're waiting for Play to confirm it.
     * Deliberately sticky after a cancel or error: any real entitlement arriving while this
     * screen is still open (e.g. a pending purchase from an earlier attempt completing) still
     * completes the purchase flow below, instead of being silently missed.
     */
    val awaitingActivation: Boolean = false,
    /** Play confirmed the purchase (backend flipped to Active after [awaitingActivation]). */
    val purchaseCompleted: Boolean = false,
    /** Feedback for the last purchase attempt's result; cleared at the start of the next attempt. */
    val purchaseNotice: PurchaseNotice? = null,
) {
    /** Price for the currently selected tier, straight from Play. Null until loaded. */
    val selectedPrice: String?
        get() =
            when (selectedTier) {
                PremiumTier.YEARLY -> formattedYearlyPrice
                PremiumTier.LIFETIME -> formattedLifetimePrice
            }

    /**
     * Never let the user buy something whose price we haven't shown them, and never start a
     * second purchase for someone Play already reports as active (a yearly subscriber buying
     * lifetime would keep paying for the subscription).
     */
    val canPurchase: Boolean
        get() = selectedPrice != null && !purchaseInFlight && backendState !is PremiumState.Active
}

/** Short-lived feedback shown on the purchase screen after a purchase attempt. */
enum class PurchaseNotice {
    /** Play accepted the order but payment isn't confirmed yet (e.g. cash). */
    PENDING,

    /** The purchase could not be completed (Play error, or launching it threw). */
    FAILED,
}
