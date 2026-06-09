package se.birdy.app.premium

import kotlinx.datetime.Instant
import se.birdy.domain.premium.PremiumState

object EntryFlowDecider {
    private const val GRACE_DAYS = 7L
    private const val THROTTLE_DAYS = 3L
    private const val DAY_MS = 24L * 3600L * 1000L

    /**
     * Show the cold-start premium modal iff all conditions hold:
     *  1. Onboarding completed
     *  2. Premium is Free (not Active)
     *  3. `firstInstallAt` is set (DataStore migration ran)
     *  4. ≥ 7 days since first install
     *  5. ≥ 3 days since last shown (null counts as "never shown")
     */
    fun shouldShowPremiumModal(
        now: Instant,
        firstInstallAt: Instant?,
        lastShownAt: Instant?,
        state: PremiumState,
        onboardingComplete: Boolean,
    ): Boolean {
        if (!onboardingComplete) return false
        if (state !is PremiumState.Free) return false
        if (firstInstallAt == null) return false
        if ((now - firstInstallAt).inWholeMilliseconds < GRACE_DAYS * DAY_MS) return false
        if (lastShownAt != null && (now - lastShownAt).inWholeMilliseconds < THROTTLE_DAYS * DAY_MS) return false
        return true
    }

    /**
     * Show the premium screen once, immediately after onboarding, iff:
     *  1. Onboarding completed
     *  2. Not shown before
     *  3. Premium is Free (not Active)
     *
     * No grace/throttle — this is the day-0 introduction, distinct from the
     * 7-day cold-start modal above.
     */
    fun shouldShowPostOnboardingPremium(
        onboardingComplete: Boolean,
        alreadyShown: Boolean,
        state: PremiumState,
    ): Boolean {
        if (!onboardingComplete) return false
        if (alreadyShown) return false
        if (state !is PremiumState.Free) return false
        return true
    }
}
