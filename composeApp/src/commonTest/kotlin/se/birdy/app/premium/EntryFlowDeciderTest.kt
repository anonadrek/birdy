package se.birdy.app.premium

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntryFlowDeciderTest {
    private val today = LocalDate(2026, 5, 12)
    private val yesterday = LocalDate(2026, 5, 11)
    private val activeYearly = PremiumState.Active(PremiumTier.YEARLY, Instant.fromEpochMilliseconds(1L))

    @Test
    fun `show when onboarding done, premium free, never shown`() {
        assertTrue(EntryFlowDecider.shouldShowPremiumModal(today, lastShown = null, state = PremiumState.Free, onboardingComplete = true))
    }

    @Test
    fun `show when last shown was yesterday`() {
        assertTrue(
            EntryFlowDecider.shouldShowPremiumModal(today, lastShown = yesterday, state = PremiumState.Free, onboardingComplete = true),
        )
    }

    @Test
    fun `skip when last shown is today`() {
        assertFalse(EntryFlowDecider.shouldShowPremiumModal(today, lastShown = today, state = PremiumState.Free, onboardingComplete = true))
    }

    @Test
    fun `skip when premium is active`() {
        assertFalse(EntryFlowDecider.shouldShowPremiumModal(today, lastShown = null, state = activeYearly, onboardingComplete = true))
    }

    @Test
    fun `skip when onboarding not complete`() {
        assertFalse(EntryFlowDecider.shouldShowPremiumModal(today, lastShown = null, state = PremiumState.Free, onboardingComplete = false))
    }

    @Test
    fun `skip when premium active even if last shown was long ago`() {
        assertFalse(
            EntryFlowDecider.shouldShowPremiumModal(
                today,
                lastShown = LocalDate(2020, 1, 1),
                state = activeYearly,
                onboardingComplete = true,
            ),
        )
    }

    @Test
    fun `show when both onboarding done and free and last shown was 2 days ago`() {
        val twoDaysAgo = LocalDate(2026, 5, 10)
        assertTrue(
            EntryFlowDecider.shouldShowPremiumModal(today, lastShown = twoDaysAgo, state = PremiumState.Free, onboardingComplete = true),
        )
    }

    @Test
    fun `skip when onboarding incomplete and last shown is null and free`() {
        assertFalse(EntryFlowDecider.shouldShowPremiumModal(today, lastShown = null, state = PremiumState.Free, onboardingComplete = false))
    }
}
