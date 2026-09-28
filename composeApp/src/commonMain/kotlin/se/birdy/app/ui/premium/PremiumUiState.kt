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
     * Premium turned on while this screen was open — a purchase this screen launched, or any
     * other Free→Active transition observed while it was open (e.g. a purchase that finished
     * while the app was killed during 3-D Secure, or a pending purchase from an earlier session
     * completing while this screen happened to be open). Not gated on this screen having
     * launched the purchase itself — see [PremiumViewModel]'s `justActivated` check.
     */
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
