package se.birdy.app.ui.settings

import org.jetbrains.compose.resources.StringResource

sealed interface SettingsEffect {
    data class RestartForLocale(
        val tag: String,
    ) : SettingsEffect

    data class ShowToast(
        val text: StringResource,
    ) : SettingsEffect

    data object OpenPrivacyUrl : SettingsEffect

    data object OpenTermsUrl : SettingsEffect

    data object RateOnPlayStore : SettingsEffect

    data object ShareApp : SettingsEffect

    data object SendFeedback : SettingsEffect

    data object OpenAbout : SettingsEffect
}
