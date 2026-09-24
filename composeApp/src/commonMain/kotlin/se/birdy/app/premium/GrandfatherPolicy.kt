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
 */
object GrandfatherPolicy {
    fun isGrandfathered(
        storedFirstInstallMs: Long?,
        packageFirstInstallMs: Long?,
        cutoffMs: Long,
    ): Boolean =
        listOfNotNull(storedFirstInstallMs, packageFirstInstallMs)
            .any { it > 0 && it < cutoffMs }
}
