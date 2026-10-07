package se.birdy.ml.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Release 1.3.0 Plan 3 Task 7 review: the scan Activity handles orientation changes itself, so
 * ImageAnalysis.targetRotation kept its bind-time value and, after the phone was turned mid-scan,
 * the classifier input and the saved photo lay on their side. AndroidCameraSource now follows the
 * physical orientation (CameraX's OrientationEventListener pattern); this pins the mapping from
 * the listener's degrees (clockwise from natural portrait) to Surface.ROTATION_0..270 (0..3).
 */
class DeviceOrientationRotationTest {
    @Test
    fun `upright and slightly tilted phones are rotation 0`() {
        assertEquals(SURFACE_ROTATION_0, surfaceRotationForDeviceOrientation(0))
        assertEquals(SURFACE_ROTATION_0, surfaceRotationForDeviceOrientation(30))
        assertEquals(SURFACE_ROTATION_0, surfaceRotationForDeviceOrientation(330))
        assertEquals(SURFACE_ROTATION_0, surfaceRotationForDeviceOrientation(359))
    }

    @Test
    fun `a phone turned clockwise onto its right side is rotation 270`() {
        assertEquals(SURFACE_ROTATION_270, surfaceRotationForDeviceOrientation(45))
        assertEquals(SURFACE_ROTATION_270, surfaceRotationForDeviceOrientation(90))
        assertEquals(SURFACE_ROTATION_270, surfaceRotationForDeviceOrientation(134))
    }

    @Test
    fun `an upside down phone is rotation 180`() {
        assertEquals(SURFACE_ROTATION_180, surfaceRotationForDeviceOrientation(135))
        assertEquals(SURFACE_ROTATION_180, surfaceRotationForDeviceOrientation(180))
        assertEquals(SURFACE_ROTATION_180, surfaceRotationForDeviceOrientation(224))
    }

    @Test
    fun `a phone turned counterclockwise onto its left side is rotation 90`() {
        assertEquals(SURFACE_ROTATION_90, surfaceRotationForDeviceOrientation(225))
        assertEquals(SURFACE_ROTATION_90, surfaceRotationForDeviceOrientation(270))
        assertEquals(SURFACE_ROTATION_90, surfaceRotationForDeviceOrientation(314))
    }

    @Test
    fun `a phone lying flat gives no rotation to apply`() {
        // OrientationEventListener.ORIENTATION_UNKNOWN
        assertNull(surfaceRotationForDeviceOrientation(-1))
    }
}
