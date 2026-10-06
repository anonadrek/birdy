package se.birdy.ml.camera

// The values of android.view.Surface.ROTATION_0..ROTATION_270, kept here so the mapping below is
// plain common code with a test in the normal gate.
const val SURFACE_ROTATION_0 = 0
const val SURFACE_ROTATION_90 = 1
const val SURFACE_ROTATION_180 = 2
const val SURFACE_ROTATION_270 = 3

private const val ORIENTATION_UNKNOWN = -1

/**
 * The target rotation (a SURFACE_ROTATION_* value) for a physical device orientation as
 * android.view.OrientationEventListener reports it: degrees clockwise from natural portrait, -1
 * when the phone lies flat. CameraX's documented mapping for apps that keep their own orientation
 * (Birdy's Activity handles configuration changes itself): a phone turned clockwise onto its right
 * side (90°) is ROTATION_270. Null for -1: keep the current rotation.
 *
 * Release 1.3.0 Plan 3 Task 7 review: without it the analysis frames kept their bind-time
 * rotation after the phone was turned mid-scan, so the classifier input and the saved photo lay
 * on their side.
 */
@Suppress("MagicNumber") // The 45° sector edges are the mapping itself.
fun surfaceRotationForDeviceOrientation(degrees: Int): Int? =
    when {
        degrees == ORIENTATION_UNKNOWN -> null
        degrees % 360 in 45 until 135 -> SURFACE_ROTATION_270
        degrees % 360 in 135 until 225 -> SURFACE_ROTATION_180
        degrees % 360 in 225 until 315 -> SURFACE_ROTATION_90
        else -> SURFACE_ROTATION_0
    }
