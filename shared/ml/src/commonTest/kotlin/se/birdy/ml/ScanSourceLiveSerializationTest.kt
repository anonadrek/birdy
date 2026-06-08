package se.birdy.ml

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanSourceLiveSerializationTest {
    @Test
    fun imageSurvivesRoundTrip() {
        val original =
            ScanSource.Image(
                frameJpegPath = "/f.jpg",
                classification = Classification(results = emptyList()),
            )
        val json = Json.encodeToString(ScanSourceSerialization.serializer(), original.toSerial())
        val restored = Json.decodeFromString<ScanSourceSerialization>(json).toScanSource()
        assertTrue(restored is ScanSource.Image)
        assertEquals("/f.jpg", (restored as ScanSource.Image).frameJpegPath)
    }
}
