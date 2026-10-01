package se.birdy.app.premium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrandfatherPolicyTest {
    private val cutoff = 1_790_892_000_000L // 2026-10-02T00:00 Europe/Stockholm

    private fun grandfathered(
        legacy: Long?,
        trusted: Long?,
        cutoffMs: Long = cutoff,
    ) = GrandfatherPolicy.isGrandfathered(
        legacyInstallMs = legacy,
        trustedFirstSeenMs = trusted,
        cutoffMs = cutoffMs,
    )

    @Test
    fun `no evidence means not grandfathered`() {
        assertFalse(grandfathered(legacy = null, trusted = null))
    }

    @Test
    fun `legacy install before cutoff is grandfathered`() {
        assertTrue(grandfathered(legacy = cutoff - 1, trusted = null))
    }

    @Test
    fun `trusted first start before cutoff is grandfathered`() {
        assertTrue(grandfathered(legacy = null, trusted = cutoff - 1))
    }

    @Test
    fun `legacy install before cutoff is grandfathered even if the trusted first start is after`() {
        assertTrue(grandfathered(legacy = cutoff - 86_400_000, trusted = cutoff + 5_000))
    }

    @Test
    fun `evidence after cutoff is not grandfathered`() {
        assertFalse(grandfathered(legacy = cutoff + 1, trusted = cutoff + 2))
    }

    @Test
    fun `evidence exactly at cutoff is not grandfathered`() {
        assertFalse(grandfathered(legacy = cutoff, trusted = cutoff))
    }

    @Test
    fun `zero or negative timestamps are treated as unknown`() {
        assertFalse(grandfathered(legacy = 0L, trusted = -1L))
    }

    @Test
    fun `cutoff zero grandfathers nobody so purchases can be tested`() {
        assertFalse(grandfathered(legacy = 1_780_000_000_000L, trusted = 1_780_000_000_000L, cutoffMs = 0L))
    }

    @Test
    fun `evidence from before the app existed is ignored`() {
        assertFalse(grandfathered(legacy = 946_684_800_000L, trusted = 946_684_800_000L))
    }

    // earliestInstallMs feeds the 7-day onboarding grace only (never early-user proof).

    @Test
    fun `earliest install with nothing stored and no package uses candidate`() {
        val candidate = 1_800_000_000_000L
        assertEquals(candidate, GrandfatherPolicy.earliestInstallMs(null, null, candidate))
    }

    @Test
    fun `earliest install with nothing stored and earlier package uses package`() {
        val candidate = 1_800_000_000_000L
        val packageMs = 1_780_000_000_000L
        assertEquals(packageMs, GrandfatherPolicy.earliestInstallMs(null, packageMs, candidate))
    }

    @Test
    fun `earliest install with stored earlier than package keeps stored unchanged`() {
        val stored = 1_780_000_000_000L
        val packageMs = 1_790_000_000_000L
        val candidate = 1_800_000_000_000L
        assertEquals(stored, GrandfatherPolicy.earliestInstallMs(stored, packageMs, candidate))
    }

    @Test
    fun `earliest install with package earlier than stored uses package`() {
        val stored = 1_790_000_000_000L
        val packageMs = 1_780_000_000_000L
        val candidate = 1_800_000_000_000L
        assertEquals(packageMs, GrandfatherPolicy.earliestInstallMs(stored, packageMs, candidate))
    }

    @Test
    fun `earliest install ignores zero or negative package`() {
        val stored = 1_790_000_000_000L
        val candidate = 1_800_000_000_000L
        assertEquals(stored, GrandfatherPolicy.earliestInstallMs(stored, 0L, candidate))
        assertEquals(stored, GrandfatherPolicy.earliestInstallMs(stored, -1L, candidate))
    }

    @Test
    fun `earliest install ignores a package time before the app existed`() {
        val candidate = 1_790_000_000_000L
        assertEquals(candidate, GrandfatherPolicy.earliestInstallMs(null, 946_684_800_000L, candidate))
    }

    @Test
    fun `both stored and package invalid falls back to candidate`() {
        val candidate = 1_790_000_000_000L
        assertEquals(candidate, GrandfatherPolicy.earliestInstallMs(0L, -5L, candidate))
    }
}
