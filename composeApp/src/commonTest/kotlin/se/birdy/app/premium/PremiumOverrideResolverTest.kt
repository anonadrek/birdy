package se.birdy.app.premium

import kotlinx.datetime.Instant
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PremiumOverrideResolverTest {
    private val now = Instant.fromEpochMilliseconds(1_790_000_000_000L)

    private fun resolve(
        grandfathered: Boolean = false,
        debugSkip: Boolean = false,
        openForLaunch: Boolean = false,
        debugForceYearly: Boolean = false,
    ) = PremiumOverrideResolver.resolve(
        isGrandfathered = grandfathered,
        debugSkipOverride = debugSkip,
        premiumOpenForLaunch = openForLaunch,
        debugForceYearly = debugForceYearly,
        now = now,
    )

    @Test
    fun `new user after monetisation gets no override so billing decides`() {
        assertNull(resolve())
    }

    @Test
    fun `grandfathered user gets lifetime premium`() {
        val state = resolve(grandfathered = true)
        assertIs<PremiumState.Active>(state)
        assertEquals(PremiumTier.LIFETIME, state.tier)
    }

    @Test
    fun `debug skip wins over grandfathered so the paywall can be tested`() {
        assertNull(resolve(grandfathered = true, debugSkip = true))
    }

    @Test
    fun `open for launch still grants lifetime when enabled`() {
        assertIs<PremiumState.Active>(resolve(openForLaunch = true))
    }

    @Test
    fun `debug force yearly grants yearly`() {
        val state = resolve(debugForceYearly = true)
        assertIs<PremiumState.Active>(state)
        assertEquals(PremiumTier.YEARLY, state.tier)
    }

    @Test
    fun `grandfathered plus debug force yearly stays lifetime`() {
        val state = resolve(grandfathered = true, debugForceYearly = true)
        assertIs<PremiumState.Active>(state)
        assertEquals(PremiumTier.LIFETIME, state.tier)
    }

    @Test
    fun `debug skip wins over open for launch too`() {
        assertNull(resolve(debugSkip = true, openForLaunch = true))
    }

    @Test
    fun `open for launch grants lifetime purchased now`() {
        val state = resolve(openForLaunch = true)
        assertIs<PremiumState.Active>(state)
        assertEquals(PremiumTier.LIFETIME, state.tier)
        assertEquals(now, state.purchasedAt)
    }

    // isEarlyMember — exercised through the resolver's own output, since the DEBUG skip
    // toggle's precedence (see PremiumOverrideResolver.isEarlyMember's KDoc) is what makes
    // gating on the override's presence different from gating on isGrandfathered alone.

    @Test
    fun `grandfathered user with no debug skip is an early member`() {
        val override = resolve(grandfathered = true)
        assertTrue(PremiumOverrideResolver.isEarlyMember(isGrandfathered = true, premiumOverride = override))
    }

    @Test
    fun `grandfathered user with debug skip is not an early member`() {
        val override = resolve(grandfathered = true, debugSkip = true)
        assertNull(override)
        assertFalse(PremiumOverrideResolver.isEarlyMember(isGrandfathered = true, premiumOverride = override))
    }

    @Test
    fun `non-grandfathered user is not an early member even with a debug force yearly override`() {
        val override = resolve(debugForceYearly = true)
        assertIs<PremiumState.Active>(override)
        assertFalse(PremiumOverrideResolver.isEarlyMember(isGrandfathered = false, premiumOverride = override))
    }
}
