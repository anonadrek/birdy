package se.birdy.app.location

/** A geographic coordinate. Named LatLng to avoid clashing with osmdroid's GeoPoint. */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

/**
 * One-shot device location. Android actual uses LocationManager (no Play Services).
 * Returns null when permission is missing, location is off, or the fix times out —
 * implementations MUST NOT throw.
 */
interface LocationProvider {
    suspend fun current(): LatLng?
}
