package se.birdy.domain.premium

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PremiumStateTest {
    @Test
    fun `Free is a singleton object`() {
        assertEquals(PremiumState.Free, PremiumState.Free)
    }

    @Test
    fun `Active equality compares tier and purchasedAt`() {
        val t = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        val a = PremiumState.Active(PremiumTier.YEARLY, t)
        val b = PremiumState.Active(PremiumTier.YEARLY, t)
        val c = PremiumState.Active(PremiumTier.LIFETIME, t)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `PremiumTier has YEARLY and LIFETIME only`() {
        assertEquals(listOf("YEARLY", "LIFETIME"), PremiumTier.entries.map { it.name })
    }
}
