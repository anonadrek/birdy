package se.birdy.domain.observation

import kotlinx.datetime.Instant

enum class ObservationSource { Photo, Audio }

data class Observation(
    val id: String,
    val speciesId: String?,
    val capturedAt: Instant,
    val savedAt: Instant,
    val photoPath: String,
    val note: String,
    val confidence: Float,
    val latitude: Double?,
    val longitude: Double?,
    val locationLabel: String?,
    val stampNumber: Int = 0,
    val audioPath: String? = null,
    val sourceType: ObservationSource = ObservationSource.Photo,
)
