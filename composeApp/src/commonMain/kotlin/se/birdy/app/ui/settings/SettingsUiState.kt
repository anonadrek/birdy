package se.birdy.app.ui.settings

import se.birdy.datastore.AppLanguage
import se.birdy.domain.premium.PremiumTier

data class SettingsUiState(
    val userName: String = "",
    val language: AppLanguage = AppLanguage.SYSTEM,
    val premiumActive: Boolean = false,
    /**
     * The tier of a real, active Play **subscription** — derived from the billing backend only,
     * never from [SettingsViewModel]'s premiumOverride. The override exists to grant Premium
     * (grandfathering, the debug "force yearly" toggle) without a Play purchase behind it, so it
     * must never gate the "Manage subscription" link: a grandfathered device whose Google account
     * also holds a real yearly subscription must still see the cancel link, and the debug
     * override must never fabricate one. Only [PremiumTier.YEARLY] has a subscription to manage
     * — Lifetime is a one-time purchase.
     */
    val playSubscriptionTier: PremiumTier? = null,
)
