package se.birdy.app.premium

import kotlinx.datetime.LocalDate
import se.birdy.domain.premium.PremiumState

object EntryFlowDecider {
    /**
     * Returns true iff all three are true:
     *  1. Onboarding has been completed.
     *  2. Premium is not active.
     *  3. The modal has not been shown today.
     */
    fun shouldShowPremiumModal(
        today: LocalDate,
        lastShown: LocalDate?,
        state: PremiumState,
        onboardingComplete: Boolean,
    ): Boolean {
        if (!onboardingComplete) return false
        if (state !is PremiumState.Free) return false
        if (lastShown == today) return false
        return true
    }
}
