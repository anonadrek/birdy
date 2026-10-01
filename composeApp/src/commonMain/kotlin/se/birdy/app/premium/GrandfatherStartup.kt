package se.birdy.app.premium

import kotlinx.coroutines.flow.first
import se.birdy.datastore.UserPreferences

/**
 * The install-time step the Android host runs once per start, before anything else writes these
 * preferences (spec 2026-09-24 §5.1, hardened 2026-10-01). The order is the point:
 *  1. `firstInstallTimestamp` is read once, before this start writes anything. On the first start
 *     of a build with the hardened rule that is exactly what an older build left behind, which is
 *     the legacy proof.
 *  2. The early-user proof is collected ([GrandfatherPolicy.collectEvidence]) and what changed is
 *     persisted: the one-time legacy capture, and the trusted first-seen time once there is one.
 *  3. Only then is `firstInstallTimestamp` repaired for the 7-day onboarding grace. That value
 *     follows the device clock and PackageInfo, so it is never proof.
 *
 * Returns the proof for this start's [GrandfatherPolicy.isGrandfathered] decision. Writes are
 * not caught: if persisting the proof fails, the repair in step 3 never runs, so a timestamp this
 * build writes can never be captured as legacy proof at a later start.
 */
object GrandfatherStartup {
    // v0.8.0-rc1 upgraders (onboarding seen, no timestamp yet) are backdated so the grace is over.
    private const val UPGRADE_INSTALL_BACKDATE_MS = 8L * 24 * 60 * 60 * 1000

    /**
     * @param packageFirstInstallMs Android's PackageInfo install time (onboarding grace only).
     * @param deviceNowMs the device clock (onboarding grace only).
     * @param trustedNowMs the network-synchronised time, or null when the platform has none; only
     *   called until a trusted time is stored.
     */
    suspend fun run(
        prefs: UserPreferences,
        packageFirstInstallMs: Long?,
        deviceNowMs: Long,
        trustedNowMs: () -> Long?,
    ): GrandfatherEvidence {
        val storedFirstInstallMs = prefs.firstInstallTimestamp.first()
        val evidence = collectAndPersistEvidence(prefs, storedFirstInstallMs, trustedNowMs)
        repairFirstInstallTimestamp(prefs, storedFirstInstallMs, packageFirstInstallMs, deviceNowMs)
        return evidence
    }

    private suspend fun collectAndPersistEvidence(
        prefs: UserPreferences,
        storedFirstInstallMs: Long?,
        trustedNowMs: () -> Long?,
    ): GrandfatherEvidence {
        val stored =
            GrandfatherEvidence(
                legacyCaptured = prefs.grandfatherLegacyCaptured.first(),
                legacyInstallMs = prefs.grandfatherLegacyInstallMs.first(),
                trustedFirstSeenMs = prefs.grandfatherTrustedFirstSeenMs.first(),
            )
        val collected = GrandfatherPolicy.collectEvidence(stored, storedFirstInstallMs, trustedNowMs)
        if (!stored.legacyCaptured) prefs.captureGrandfatherLegacy(collected.legacyInstallMs)
        val newTrustedMs = collected.trustedFirstSeenMs
        if (stored.trustedFirstSeenMs == null && newTrustedMs != null) {
            prefs.setGrandfatherTrustedFirstSeenMs(newTrustedMs)
        }
        return collected
    }

    private suspend fun repairFirstInstallTimestamp(
        prefs: UserPreferences,
        storedFirstInstallMs: Long?,
        packageFirstInstallMs: Long?,
        deviceNowMs: Long,
    ) {
        val candidateMs =
            if (prefs.hasSeenOnboarding.first()) deviceNowMs - UPGRADE_INSTALL_BACKDATE_MS else deviceNowMs
        val resolvedMs = GrandfatherPolicy.earliestInstallMs(storedFirstInstallMs, packageFirstInstallMs, candidateMs)
        if (resolvedMs != storedFirstInstallMs) prefs.setFirstInstallTimestamp(resolvedMs)
    }
}
