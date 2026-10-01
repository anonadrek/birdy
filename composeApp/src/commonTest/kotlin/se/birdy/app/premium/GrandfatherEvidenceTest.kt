package se.birdy.app.premium

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [GrandfatherPolicy.collectEvidence]: what one start adds to the stored early-user proof. */
class GrandfatherEvidenceTest {
    private val juneMs = Instant.parse("2026-06-09T08:00:00Z").toEpochMilliseconds()
    private val octoberMs = Instant.parse("2026-10-10T08:00:00Z").toEpochMilliseconds()
    private val beforeBirdyMs = Instant.parse("2000-01-01T00:00:00Z").toEpochMilliseconds()

    private val nothingStored = GrandfatherEvidence()

    private fun collect(
        stored: GrandfatherEvidence,
        legacy: Long? = null,
        trusted: Long? = null,
    ) = GrandfatherPolicy.collectEvidence(
        stored = stored,
        legacyFirstInstallMs = legacy,
        trustedNowMs = { trusted },
    )

    @Test
    fun `nothing is stored by default`() {
        assertEquals(GrandfatherEvidence(legacyCaptured = false, legacyInstallMs = null, trustedFirstSeenMs = null), nothingStored)
    }

    @Test
    fun `first start captures the legacy install time`() {
        val collected = collect(nothingStored, legacy = juneMs)
        assertEquals(GrandfatherEvidence(legacyCaptured = true, legacyInstallMs = juneMs), collected)
    }

    @Test
    fun `first start without a legacy install time still marks the capture done`() {
        val collected = collect(nothingStored, legacy = null)
        assertTrue(collected.legacyCaptured)
        assertNull(collected.legacyInstallMs)
    }

    @Test
    fun `a legacy time from before the app existed is captured as nothing`() {
        val collected = collect(nothingStored, legacy = beforeBirdyMs)
        assertTrue(collected.legacyCaptured)
        assertNull(collected.legacyInstallMs)
    }

    @Test
    fun `once captured a later legacy time never counts`() {
        val capturedNothing = GrandfatherEvidence(legacyCaptured = true, legacyInstallMs = null)
        assertEquals(capturedNothing, collect(capturedNothing, legacy = juneMs))
    }

    @Test
    fun `once captured the captured time is kept`() {
        val capturedJune = GrandfatherEvidence(legacyCaptured = true, legacyInstallMs = juneMs)
        assertEquals(juneMs, collect(capturedJune, legacy = octoberMs).legacyInstallMs)
    }

    @Test
    fun `trusted time is stored when none is stored yet`() {
        assertEquals(octoberMs, collect(nothingStored, trusted = octoberMs).trustedFirstSeenMs)
    }

    @Test
    fun `no trusted time available stores nothing so the next start asks again`() {
        assertNull(collect(nothingStored, trusted = null).trustedFirstSeenMs)
    }

    @Test
    fun `a trusted time from before the app existed is not stored`() {
        assertNull(collect(nothingStored, trusted = beforeBirdyMs).trustedFirstSeenMs)
    }

    @Test
    fun `stored trusted time is never overwritten and the clock is not asked again`() {
        var asked = 0
        val stored = GrandfatherEvidence(legacyCaptured = true, trustedFirstSeenMs = juneMs)
        val collected =
            GrandfatherPolicy.collectEvidence(
                stored = stored,
                legacyFirstInstallMs = null,
                trustedNowMs = {
                    asked++
                    octoberMs
                },
            )
        assertEquals(juneMs, collected.trustedFirstSeenMs)
        assertEquals(0, asked)
    }
}
