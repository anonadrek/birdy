package se.birdy.app.premium

import kotlinx.datetime.Instant
import se.birdy.domain.premium.PremiumState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntryFlowDeciderTest {
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val day = 24L * 3600L * 1000L

    @Test fun `returns false if onboarding incomplete`() {
        val r =
            EntryFlowDecider.shouldShowPremiumModal(
                now = now,
                firstInstallAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 30 * day),
                lastShownAt = null,
                state = PremiumState.Free,
                onboardingComplete = false,
            )
        assertFalse(r)
    }

    @Test fun `returns false if premium active`() {
        val r =
            EntryFlowDecider.shouldShowPremiumModal(
                now = now,
                firstInstallAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 30 * day),
                lastShownAt = null,
                state = PremiumState.Active(se.birdy.domain.premium.PremiumTier.YEARLY, now),
                onboardingComplete = true,
            )
        assertFalse(r)
    }

    @Test fun `returns false if firstInstall is null`() {
        val r =
            EntryFlowDecider.shouldShowPremiumModal(
                now = now,
                firstInstallAt = null,
                lastShownAt = null,
                state = PremiumState.Free,
                onboardingComplete = true,
            )
        assertFalse(r)
    }

    @Test fun `returns false inside 7-day grace period`() {
        val r =
            EntryFlowDecider.shouldShowPremiumModal(
                now = now,
                firstInstallAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 5 * day),
                lastShownAt = null,
                state = PremiumState.Free,
                onboardingComplete = true,
            )
        assertFalse(r)
    }

    @Test fun `returns true after 7-day grace + never shown`() {
        val r =
            EntryFlowDecider.shouldShowPremiumModal(
                now = now,
                firstInstallAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 8 * day),
                lastShownAt = null,
                state = PremiumState.Free,
                onboardingComplete = true,
            )
        assertTrue(r)
    }

    @Test fun `returns false within 3-day throttle`() {
        val r =
            EntryFlowDecider.shouldShowPremiumModal(
                now = now,
                firstInstallAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 30 * day),
                lastShownAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 2 * day),
                state = PremiumState.Free,
                onboardingComplete = true,
            )
        assertFalse(r)
    }

    @Test fun `returns true after 3-day throttle expires`() {
        val r =
            EntryFlowDecider.shouldShowPremiumModal(
                now = now,
                firstInstallAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 30 * day),
                lastShownAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 4 * day),
                state = PremiumState.Free,
                onboardingComplete = true,
            )
        assertTrue(r)
    }

    @Test fun `post-onboarding false when onboarding incomplete`() {
        assertFalse(
            EntryFlowDecider.shouldShowPostOnboardingPremium(
                onboardingComplete = false,
                alreadyShown = false,
                state = PremiumState.Free,
            ),
        )
    }

    @Test fun `post-onboarding false when already shown`() {
        assertFalse(
            EntryFlowDecider.shouldShowPostOnboardingPremium(
                onboardingComplete = true,
                alreadyShown = true,
                state = PremiumState.Free,
            ),
        )
    }

    @Test fun `post-onboarding false when premium active`() {
        assertFalse(
            EntryFlowDecider.shouldShowPostOnboardingPremium(
                onboardingComplete = true,
                alreadyShown = false,
                state = PremiumState.Active(se.birdy.domain.premium.PremiumTier.LIFETIME, now),
            ),
        )
    }

    @Test fun `post-onboarding true when complete, not shown, free`() {
        assertTrue(
            EntryFlowDecider.shouldShowPostOnboardingPremium(
                onboardingComplete = true,
                alreadyShown = false,
                state = PremiumState.Free,
            ),
        )
    }
}
