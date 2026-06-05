package se.birdy.app.ui.map

import se.birdy.domain.observation.Observation

/** A single observation rendered on the map. */
data class MapPin(
    val observationId: String,
    val latitude: Double,
    val longitude: Double,
    val speciesId: String?,
    val stampNumber: Int,
    val photoPath: String,
)

/** Pure mapping from observations to map pins. Drops rows without both coordinates. */
object MapPinMapper {
    fun toPins(observations: List<Observation>): List<MapPin> =
        observations.mapNotNull { o ->
            val lat = o.latitude
            val lng = o.longitude
            if (lat == null || lng == null) {
                null
            } else {
                MapPin(
                    observationId = o.id,
                    latitude = lat,
                    longitude = lng,
                    speciesId = o.speciesId,
                    stampNumber = o.stampNumber,
                    photoPath = o.photoPath,
                )
            }
        }
}
