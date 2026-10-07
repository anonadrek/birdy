package se.birdy.app.ui.settings

import se.birdy.datastore.AppLanguage
import se.birdy.domain.premium.PremiumTier

data class SettingsUiState(
    val userName: String = "",
    val language: AppLanguage = AppLanguage.SYSTEM,
    val premiumActive: Boolean = false,
    /**
     * The active tier, when [premiumActive] is true (from billing or [SettingsViewModel]'s
     * premiumOverride). Only [PremiumTier.YEARLY] has a Play subscription to manage — Lifetime
     * is a one-time purchase and early-member/debug overrides are not real Play subscriptions
     * either, so the "Manage subscription" row only ever shows for a real yearly sub.
     */
    val premiumTier: PremiumTier? = null,
)
