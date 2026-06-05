package se.birdy.ml

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanSourceLiveSerializationTest {
    @Test
    fun imageLiveFlagSurvivesRoundTrip() {
        val original =
            ScanSource.Image(
                frameJpegPath = "/f.jpg",
                classification = Classification(results = emptyList()),
                live = false,
            )
        val json = Json.encodeToString(ScanSourceSerialization.serializer(), original.toSerial())
        val restored = Json.decodeFromString<ScanSourceSerialization>(json).toScanSource()
        assertTrue(restored is ScanSource.Image)
        assertEquals(false, (restored as ScanSource.Image).live)
    }
}
