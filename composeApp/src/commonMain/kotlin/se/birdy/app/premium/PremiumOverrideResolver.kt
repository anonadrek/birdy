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
}
