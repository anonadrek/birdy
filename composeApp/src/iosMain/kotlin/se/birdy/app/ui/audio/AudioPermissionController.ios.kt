package se.birdy.app.ui.audio

import kotlinx.coroutines.flow.StateFlow

actual interface AudioPermissionController {
    actual val state: StateFlow<PermissionState>

    actual fun request()

    actual fun openSettings()

    actual fun recheck()
}
