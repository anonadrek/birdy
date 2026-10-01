package se.birdy.app.premium

/**
 * The persisted early-user proof (spec 2026-09-24 §5.1, hardened 2026-10-01). Mirrors the three
 * `grandfather*` values in `UserPreferences`; collected by [GrandfatherPolicy.collectEvidence] and
 * judged by [GrandfatherPolicy.isGrandfathered].
 *
 * @property legacyCaptured true once the first start of a build with the hardened rule has read
 *   the install time an older build left behind (done exactly once).
 * @property legacyInstallMs that install time, if it was plausible; null = nothing to keep.
 * @property trustedFirstSeenMs network time at the first start where the platform had one;
 *   null = none seen yet.
 */
data class GrandfatherEvidence(
    val legacyCaptured: Boolean = false,
    val legacyInstallMs: Long? = null,
    val trustedFirstSeenMs: Long? = null,
)
