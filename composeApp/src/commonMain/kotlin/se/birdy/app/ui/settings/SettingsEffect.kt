package se.birdy.app.ui.settings

import org.jetbrains.compose.resources.StringResource

sealed interface SettingsEffect {
    /** Applicerar valt språk. AppCompat/LocaleManager recreatar aktiviteten automatiskt — ingen omstart behövs. */
    data class ApplyLocale(
        val tag: String,
    ) : SettingsEffect

    data class ShowToast(
        val text: StringResource,
    ) : SettingsEffect

    data object OpenPrivacyUrl : SettingsEffect

    data object OpenTermsUrl : SettingsEffect

    data object OpenWebsiteUrl : SettingsEffect

    data object RateOnPlayStore : SettingsEffect

    data object ShareApp : SettingsEffect

    data object SendFeedback : SettingsEffect

    data object OpenAbout : SettingsEffect

    /** Opens Google Play's subscription center for the yearly plan (Play policy 9900533). */
    data object OpenManageSubscriptionUrl : SettingsEffect
}
