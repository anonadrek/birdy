package se.birdy.app.premium

/**
 * Early-user ("grandfather") rule — decided by Albin 2026-06-17, specced 2026-09-24 §5.1:
 * everyone who installed Birdy before monetisation went live keeps Premium forever.
 *
 * Recomputed on every app start from stable inputs; nothing is persisted. That keeps a
 * billing-verify build (cutoff 0) from poisoning later builds, and the stored
 * `firstInstallTimestamp` travels with Google backup to a new phone.
 *
 * The cutoff passed in by the app MUST NEVER change after 1.3.0 ships — moving it would
 * silently grant or revoke Premium for real users.
 *
 * Both functions floor out any timestamp earlier than [EARLIEST_PLAUSIBLE_INSTALL_MS]: a
 * garbage-but-positive `PackageInfo.firstInstallTime` (e.g. an unset device clock on first
 * boot reporting 1970/2000-ish) must never be treated as a real early install, persisted, or
 * backed up — Birdy did not exist before that date.
 */
object GrandfatherPolicy {
    // 2026-04-01T00:00Z — Birdy did not exist before this.
    private const val EARLIEST_PLAUSIBLE_INSTALL_MS = 1_775_001_600_000L

    fun isGrandfathered(
        storedFirstInstallMs: Long?,
        packageFirstInstallMs: Long?,
        cutoffMs: Long,
    ): Boolean =
        listOfNotNull(storedFirstInstallMs, packageFirstInstallMs)
            .any { it >= EARLIEST_PLAUSIBLE_INSTALL_MS && it < cutoffMs }

    /**
     * The install time to persist: the earliest plausible value among what we already stored
     * (or [candidateMs] if nothing is stored yet) and Android's package install time. Only ever
     * moves the stored value earlier, so the backed-up timestamp carries the true first install
     * to a new phone. An invalid stored or package value never blocks falling back to
     * [candidateMs].
     */
    fun earliestInstallMs(
        storedMs: Long?,
        packageMs: Long?,
        candidateMs: Long,
    ): Long =
        listOfNotNull(storedMs, packageMs)
            .filter { it >= EARLIEST_PLAUSIBLE_INSTALL_MS }
            .minOrNull()
            ?: candidateMs
}
