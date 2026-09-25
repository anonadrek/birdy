package se.birdy.app.premium

import kotlinx.datetime.Instant
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

/**
 * Decides the app-wide premium override (null = let Play Billing decide).
 * Order matters: the DEBUG billing-verify skip wins over everything so the paywall can be
 * exercised on a developer device that is itself grandfathered. Callers pass
 * `debugSkipOverride`/`debugForceYearly` already AND-ed with BuildConfig.DEBUG.
 */
object PremiumOverrideResolver {
    fun resolve(
        isGrandfathered: Boolean,
        debugSkipOverride: Boolean,
        premiumOpenForLaunch: Boolean,
        debugForceYearly: Boolean,
        now: Instant,
    ): PremiumState? =
        when {
            debugSkipOverride -> null
            isGrandfathered -> PremiumState.Active(PremiumTier.LIFETIME, now)
            premiumOpenForLaunch -> PremiumState.Active(PremiumTier.LIFETIME, now)
            debugForceYearly -> PremiumState.Active(PremiumTier.YEARLY, now)
            else -> null
        }

    /**
     * Whether the current user should see the early-member thank-you (1.3.0 spec §5.2)
     * instead of any paywall. [premiumOverride] is the value [resolve] returned — the DEBUG
     * "Skip premium override" toggle nulls it even for a grandfathered user (so a grandfathered
     * developer phone can still exercise the real paywall for Billing-verify testing), so this
     * gates on the override actually being present, not just [isGrandfathered] on its own.
     */
    fun isEarlyMember(
        isGrandfathered: Boolean,
        premiumOverride: PremiumState?,
    ): Boolean = isGrandfathered && premiumOverride != null
}
