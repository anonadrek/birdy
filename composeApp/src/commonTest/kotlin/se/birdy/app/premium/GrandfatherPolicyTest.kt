package se.birdy.app.premium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrandfatherPolicyTest {
    private val cutoff = 1_790_892_000_000L // 2026-10-02T00:00 Europe/Stockholm

    @Test
    fun `no install data means not grandfathered`() {
        assertFalse(GrandfatherPolicy.isGrandfathered(null, null, cutoff))
    }

    @Test
    fun `stored first install before cutoff is grandfathered`() {
        assertTrue(GrandfatherPolicy.isGrandfathered(cutoff - 1, null, cutoff))
    }

    @Test
    fun `package first install before cutoff is grandfathered even if stored is after`() {
        assertTrue(GrandfatherPolicy.isGrandfathered(cutoff + 5_000, cutoff - 86_400_000, cutoff))
    }

    @Test
    fun `both sources after cutoff is not grandfathered`() {
        assertFalse(GrandfatherPolicy.isGrandfathered(cutoff + 1, cutoff + 2, cutoff))
    }

    @Test
    fun `install exactly at cutoff is not grandfathered`() {
        assertFalse(GrandfatherPolicy.isGrandfathered(cutoff, cutoff, cutoff))
    }

    @Test
    fun `zero or negative timestamps are treated as unknown`() {
        assertFalse(GrandfatherPolicy.isGrandfathered(0L, -1L, cutoff))
    }

    @Test
    fun `cutoff zero grandfathers nobody so purchases can be tested`() {
        assertFalse(GrandfatherPolicy.isGrandfathered(1_750_000_000_000L, 1_750_000_000_000L, 0L))
    }

    @Test
    fun `earliest install with nothing stored and no package uses candidate`() {
        val candidate = 1_800_000_000_000L
        assertEquals(candidate, GrandfatherPolicy.earliestInstallMs(null, null, candidate))
    }

    @Test
    fun `earliest install with nothing stored and earlier package uses package`() {
        val candidate = 1_800_000_000_000L
        val packageMs = 1_700_000_000_000L
        assertEquals(packageMs, GrandfatherPolicy.earliestInstallMs(null, packageMs, candidate))
    }

    @Test
    fun `earliest install with stored earlier than package keeps stored unchanged`() {
        val stored = 1_700_000_000_000L
        val packageMs = 1_750_000_000_000L
        val candidate = 1_800_000_000_000L
        assertEquals(stored, GrandfatherPolicy.earliestInstallMs(stored, packageMs, candidate))
    }

    @Test
    fun `earliest install with package earlier than stored uses package`() {
        val stored = 1_750_000_000_000L
        val packageMs = 1_700_000_000_000L
        val candidate = 1_800_000_000_000L
        assertEquals(packageMs, GrandfatherPolicy.earliestInstallMs(stored, packageMs, candidate))
    }

    @Test
    fun `earliest install ignores zero or negative package`() {
        val stored = 1_750_000_000_000L
        val candidate = 1_800_000_000_000L
        assertEquals(stored, GrandfatherPolicy.earliestInstallMs(stored, 0L, candidate))
        assertEquals(stored, GrandfatherPolicy.earliestInstallMs(stored, -1L, candidate))
    }
}
