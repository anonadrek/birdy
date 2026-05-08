package se.birdy.app.ui.settings

import se.birdy.datastore.AppLanguage

data class SettingsUiState(
    val userName: String,
    val language: AppLanguage,
)
