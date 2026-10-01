package se.birdy.app.premium

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.app.testing.FakeUserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Whole app starts the way MainActivity runs them: [GrandfatherStartup.run] with this start's
 * inputs, then [GrandfatherPolicy.isGrandfathered] with the build's cutoff. Each `start` call is
 * one cold start on the same phone (same preferences).
 */
class GrandfatherStartupTest {
    private val cutoffMs = ms("2026-10-01T22:00:00Z") // 2026-10-02 00:00 Stockholm
    private val juneMs = ms("2026-06-09T08:00:00Z") // an early 1.2.x install
    private val windowMs = ms("2026-09-30T18:00:00Z") // in the 48 h before the cutoff
    private val octoberMs = ms("2026-10-10T08:00:00Z") // after the cutoff
    private val novemberMs = ms("2026-11-09T08:00:00Z")
    private val dayMs = 86_400_000L

    private suspend fun FakeUserPreferences.start(
        deviceNowMs: Long,
        trustedNowMs: Long?,
        packageFirstInstallMs: Long? = null,
        cutoff: Long = cutoffMs,
    ): Boolean {
        val evidence =
            GrandfatherStartup.run(
                prefs = this,
                packageFirstInstallMs = packageFirstInstallMs,
                deviceNowMs = deviceNowMs,
                trustedNowMs = { trustedNowMs },
            )
        return GrandfatherPolicy.isGrandfathered(
            legacyInstallMs = evidence.legacyInstallMs,
            trustedFirstSeenMs = evidence.trustedFirstSeenMs,
            cutoffMs = cutoff,
        )
    }

    /** Google backup restores the whole DataStore file onto the new phone. */
    private suspend fun FakeUserPreferences.restoredFrom(old: FakeUserPreferences): FakeUserPreferences {
        old.firstInstallTimestamp.first()?.let { setFirstInstallTimestamp(it) }
        setHasSeenOnboarding(old.hasSeenOnboarding.first())
        if (old.grandfatherLegacyCaptured.first()) captureGrandfatherLegacy(old.grandfatherLegacyInstallMs.first())
        old.grandfatherTrustedFirstSeenMs.first()?.let { setGrandfatherTrustedFirstSeenMs(it) }
        return this
    }

    /** A phone where 1.2.x (or the vC128 purchase-test build) already wrote the install time. */
    private suspend fun installedOn12(installedMs: Long) =
        FakeUserPreferences().apply {
            setFirstInstallTimestamp(installedMs)
            setHasSeenOnboarding(true)
        }

    @Test
    fun `early user updating from 1_2 after the cutoff stays grandfathered`() =
        runTest {
            val prefs = installedOn12(juneMs)
            assertTrue(prefs.start(deviceNowMs = octoberMs, trustedNowMs = octoberMs, packageFirstInstallMs = juneMs))
            assertTrue(prefs.start(deviceNowMs = novemberMs, trustedNowMs = novemberMs, packageFirstInstallMs = juneMs))
            assertEquals(juneMs, prefs.grandfatherLegacyInstallMs.first())
        }

    @Test
    fun `new install with the device clock set back is not grandfathered`() =
        runTest {
            // Clock set to June before installing: the device clock and PackageInfo both say June.
            val prefs = FakeUserPreferences()
            assertFalse(prefs.start(deviceNowMs = juneMs, trustedNowMs = octoberMs, packageFirstInstallMs = juneMs))
            // Clock put back to the real time, app started again: still not.
            assertFalse(prefs.start(deviceNowMs = octoberMs, trustedNowMs = octoberMs, packageFirstInstallMs = juneMs))
            // The onboarding grace still uses the backdated install time; it is just never proof.
            assertEquals(juneMs, prefs.firstInstallTimestamp.first())
        }

    @Test
    fun `new install with the clock set back and no network time is not grandfathered either`() =
        runTest {
            val prefs = FakeUserPreferences()
            assertFalse(prefs.start(deviceNowMs = juneMs, trustedNowMs = null, packageFirstInstallMs = juneMs))
            assertFalse(prefs.start(deviceNowMs = juneMs + dayMs, trustedNowMs = null, packageFirstInstallMs = juneMs))
            assertFalse(prefs.start(deviceNowMs = octoberMs, trustedNowMs = octoberMs, packageFirstInstallMs = juneMs))
        }

    @Test
    fun `legacy time is captured once so a value written afterwards never counts`() =
        runTest {
            val prefs = FakeUserPreferences()
            assertFalse(prefs.start(deviceNowMs = octoberMs, trustedNowMs = null))
            // Anything that writes a backdated install time after the capture.
            prefs.setFirstInstallTimestamp(juneMs)
            assertFalse(prefs.start(deviceNowMs = octoberMs + dayMs, trustedNowMs = octoberMs + dayMs))
            assertTrue(prefs.grandfatherLegacyCaptured.first())
            assertNull(prefs.grandfatherLegacyInstallMs.first())
        }

    @Test
    fun `first start with network time before the cutoff is grandfathered for good`() =
        runTest {
            val prefs = FakeUserPreferences()
            assertTrue(prefs.start(deviceNowMs = windowMs, trustedNowMs = windowMs))
            assertTrue(prefs.start(deviceNowMs = octoberMs, trustedNowMs = octoberMs))
            assertEquals(windowMs, prefs.grandfatherTrustedFirstSeenMs.first())
        }

    @Test
    fun `first start with network time after the cutoff is not grandfathered`() =
        runTest {
            val prefs = FakeUserPreferences()
            assertFalse(prefs.start(deviceNowMs = octoberMs, trustedNowMs = octoberMs))
            assertEquals(octoberMs, prefs.grandfatherTrustedFirstSeenMs.first())
        }

    @Test
    fun `without network time the trusted time is taken at a later start`() =
        runTest {
            val prefs = FakeUserPreferences()
            // Offline since boot, or Android 12 and older: nothing trusted yet.
            assertFalse(prefs.start(deviceNowMs = windowMs, trustedNowMs = null))
            assertNull(prefs.grandfatherTrustedFirstSeenMs.first())
            val anHourLaterMs = windowMs + 3_600_000L
            assertTrue(prefs.start(deviceNowMs = anHourLaterMs, trustedNowMs = anHourLaterMs))
        }

    @Test
    fun `purchase-test build with cutoff 0 then the production build judges correctly`() =
        runTest {
            val early = installedOn12(juneMs)
            assertFalse(early.start(deviceNowMs = windowMs, trustedNowMs = windowMs, cutoff = 0L))
            assertTrue(early.start(deviceNowMs = octoberMs, trustedNowMs = octoberMs))

            val inWindow = FakeUserPreferences()
            assertFalse(inWindow.start(deviceNowMs = windowMs, trustedNowMs = windowMs, cutoff = 0L))
            assertTrue(inWindow.start(deviceNowMs = octoberMs, trustedNowMs = octoberMs))

            val late = FakeUserPreferences()
            assertFalse(late.start(deviceNowMs = octoberMs, trustedNowMs = octoberMs, cutoff = 0L))
            assertFalse(late.start(deviceNowMs = novemberMs, trustedNowMs = novemberMs))
        }

    @Test
    fun `restored backup with captured evidence is grandfathered on a new phone`() =
        runTest {
            val oldPhone = installedOn12(juneMs)
            assertTrue(oldPhone.start(deviceNowMs = octoberMs, trustedNowMs = octoberMs, packageFirstInstallMs = juneMs))
            // On the new phone PackageInfo and both clocks say November.
            val newPhone = FakeUserPreferences().restoredFrom(oldPhone)
            assertTrue(newPhone.start(deviceNowMs = novemberMs, trustedNowMs = novemberMs, packageFirstInstallMs = novemberMs))
        }

    @Test
    fun `backup made on 1_2 and restored onto 1_3 is captured on the new phone`() =
        runTest {
            val newPhone = installedOn12(juneMs)
            assertTrue(newPhone.start(deviceNowMs = novemberMs, trustedNowMs = novemberMs, packageFirstInstallMs = novemberMs))
        }

    @Test
    fun `restored backup without early evidence stays not grandfathered`() =
        runTest {
            val oldPhone = FakeUserPreferences()
            assertFalse(oldPhone.start(deviceNowMs = octoberMs, trustedNowMs = octoberMs))
            val newPhone = FakeUserPreferences().restoredFrom(oldPhone)
            assertFalse(newPhone.start(deviceNowMs = novemberMs, trustedNowMs = novemberMs, packageFirstInstallMs = novemberMs))
        }

    // The onboarding-grace timestamp (moved unchanged from MainActivity).

    @Test
    fun `fresh install records the device time for the onboarding grace`() =
        runTest {
            val prefs = FakeUserPreferences()
            prefs.start(deviceNowMs = octoberMs, trustedNowMs = null)
            assertEquals(octoberMs, prefs.firstInstallTimestamp.first())
        }

    @Test
    fun `upgrader who already saw onboarding but has no install time is backdated eight days`() =
        runTest {
            val prefs = FakeUserPreferences().apply { setHasSeenOnboarding(true) }
            prefs.start(deviceNowMs = octoberMs, trustedNowMs = null)
            assertEquals(octoberMs - 8 * dayMs, prefs.firstInstallTimestamp.first())
        }

    @Test
    fun `an earlier package install time moves the onboarding timestamp earlier`() =
        runTest {
            val prefs = FakeUserPreferences().apply { setFirstInstallTimestamp(octoberMs) }
            prefs.start(deviceNowMs = novemberMs, trustedNowMs = null, packageFirstInstallMs = windowMs)
            assertEquals(windowMs, prefs.firstInstallTimestamp.first())
        }

    @Test
    fun `stored onboarding timestamp is kept when nothing earlier is known`() =
        runTest {
            val prefs = FakeUserPreferences().apply { setFirstInstallTimestamp(juneMs) }
            prefs.start(deviceNowMs = novemberMs, trustedNowMs = null, packageFirstInstallMs = octoberMs)
            assertEquals(juneMs, prefs.firstInstallTimestamp.first())
        }

    private companion object {
        fun ms(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()
    }
}
