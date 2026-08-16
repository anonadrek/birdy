package se.birdy.app.ui.audio

import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import kotlin.test.Test
import kotlin.test.assertEquals

class IosAudioPermissionControllerTest {
    @Test
    fun mapsRecordPermissionToCommonStates() {
        assertEquals(PermissionState.Unknown, mapRecordPermission(AVAudioSessionRecordPermissionUndetermined))
        assertEquals(PermissionState.Granted, mapRecordPermission(AVAudioSessionRecordPermissionGranted))
        // iOS har ingen "fråga igen"-nivå: denied ⇒ endast Inställningar hjälper.
        assertEquals(PermissionState.PermanentlyDenied, mapRecordPermission(AVAudioSessionRecordPermissionDenied))
    }
}
