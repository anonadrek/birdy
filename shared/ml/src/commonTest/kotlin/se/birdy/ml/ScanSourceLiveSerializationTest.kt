package se.birdy.ml

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanSourceLiveSerializationTest {
    @Test
    fun imageOriginAndExifSurviveRoundTrip() {
        val original =
            ScanSource.Image(
                frameJpegPath = "/f.jpg",
                classification = Classification(results = emptyList()),
                origin = ImageOrigin.Gallery,
                exifLatitude = 59.3293,
                exifLongitude = 18.0686,
            )
        val json = Json.encodeToString(ScanSourceSerialization.serializer(), original.toSerial())
        val restored = Json.decodeFromString<ScanSourceSerialization>(json).toScanSource()
        assertTrue(restored is ScanSource.Image)
        restored as ScanSource.Image
        assertEquals(ImageOrigin.Gallery, restored.origin)
        assertEquals(59.3293, restored.exifLatitude)
        assertEquals(18.0686, restored.exifLongitude)
    }
}
