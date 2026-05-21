package se.birdy.app.ui.audio

import kotlinx.coroutines.flow.StateFlow

expect interface AudioPermissionController {
    val state: StateFlow<PermissionState>

    fun request()

    fun openSettings()

    fun recheck()
}

enum class PermissionState { Unknown, Granted, Denied, PermanentlyDenied }
