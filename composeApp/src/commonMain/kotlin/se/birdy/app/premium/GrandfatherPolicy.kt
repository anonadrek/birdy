package se.birdy.app.premium

/**
 * Early-user ("grandfather") rule — decided by Albin 2026-06-17, specced 2026-09-24 §5.1,
 * hardened 2026-10-01 against a device clock set back by hand: everyone who used Birdy before
 * monetisation went live keeps Premium forever.
 *
 * Proof is collected once and kept ([GrandfatherEvidence], persisted by [GrandfatherStartup]);
 * the decision itself is recomputed on every start from that proof and the build's cutoff, and
 * is never stored. Two kinds of proof count:
 *  - **legacy:** `firstInstallTimestamp` exactly as a build from before the hardening (1.2.x, or
 *    the vC128 purchase-test build) left it. Those builds wrote it before a backdated clock could
 *    gain anything. It is read once, on the first start of a build with this rule and before that
 *    build writes anything; a timestamp written by that build or any later one is never proof.
 *  - **trusted:** the network-synchronised time (Android 13+, which the user cannot change in the
 *    phone's settings) at the first start where the platform had one.
 *
 * The device clock and `PackageInfo.firstInstallTime` are NOT proof: both follow a clock that can
 * be set back before installing. Collection never looks at the cutoff, so a purchase-test build
 * (cutoff 0) gathers exactly what the production build later judges. The proof lives in DataStore
 * and travels with Google backup to a new phone.
 *
 * The cutoff passed in by the app MUST NEVER change after 1.3.0 ships — moving it would
 * silently grant or revoke Premium for real users.
 *
 * Every timestamp earlier than [EARLIEST_PLAUSIBLE_INSTALL_MS] is ignored: Birdy did not exist
 * before that date, so such a value is an unset clock (1970/2000-ish), never a real early install,
 * and it is neither kept as proof nor counted.
 */
object GrandfatherPolicy {
    // 2026-04-01T00:00Z — Birdy did not exist before this.
    private const val EARLIEST_PLAUSIBLE_INSTALL_MS = 1_775_001_600_000L

    fun isGrandfathered(
        legacyInstallMs: Long?,
        trustedFirstSeenMs: Long?,
        cutoffMs: Long,
    ): Boolean =
        listOfNotNull(legacyInstallMs, trustedFirstSeenMs)
            .any { it.isPlausible() && it < cutoffMs }

    /**
     * Folds one start's inputs into the [stored] proof and returns the new proof (pure; the
     * caller persists what changed).
     * - Legacy not captured yet: keeps [legacyFirstInstallMs] if plausible, else nothing, and
     *   marks the capture done. Exactly once, also when there is nothing to keep. The caller must
     *   pass `firstInstallTimestamp` as read before this start wrote anything.
     * - No trusted time yet: asks [trustedNowMs] (null = the platform has none right now) and keeps
     *   a plausible answer; otherwise a later start asks again. Never asked once one is stored.
     */
    fun collectEvidence(
        stored: GrandfatherEvidence,
        legacyFirstInstallMs: Long?,
        trustedNowMs: () -> Long?,
    ): GrandfatherEvidence {
        val withLegacy =
            if (stored.legacyCaptured) {
                stored
            } else {
                stored.copy(
                    legacyCaptured = true,
                    legacyInstallMs = legacyFirstInstallMs?.takeIf { it.isPlausible() },
                )
            }
        if (withLegacy.trustedFirstSeenMs != null) return withLegacy
        return withLegacy.copy(trustedFirstSeenMs = trustedNowMs()?.takeIf { it.isPlausible() })
    }

    /**
     * The install time to keep in `firstInstallTimestamp`, which only drives the 7-day onboarding
     * grace (never early-user proof since 2026-10-01): the earliest plausible value among what we
     * already stored (or [candidateMs] if nothing is stored yet) and Android's package install time.
     * Only ever moves the stored value earlier, so the backed-up timestamp carries the first install
     * to a new phone. An invalid stored or package value never blocks falling back to [candidateMs].
     */
    fun earliestInstallMs(
        storedMs: Long?,
        packageMs: Long?,
        candidateMs: Long,
    ): Long =
        listOfNotNull(storedMs, packageMs)
            .filter { it.isPlausible() }
            .minOrNull()
            ?: candidateMs

    private fun Long.isPlausible(): Boolean = this >= EARLIEST_PLAUSIBLE_INSTALL_MS
}
