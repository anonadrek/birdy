package se.birdy.app.ui.match

import se.birdy.ml.Classification
import se.birdy.ml.ScanSource
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchThresholdsTest {
    @Test
    fun photo_thresholds_are_0_50_0_35_0_15() {
        assertEquals(0.50f, MatchThresholds.PHOTO.matchConfidence)
        assertEquals(0.35f, MatchThresholds.PHOTO.disambigConfidence)
        assertEquals(0.15f, MatchThresholds.PHOTO.noBirdHintFloor)
    }

    @Test
    fun audio_thresholds_are_0_50_0_20_0_10() {
        assertEquals(0.50f, MatchThresholds.AUDIO.matchConfidence)
        assertEquals(0.20f, MatchThresholds.AUDIO.disambigConfidence)
        assertEquals(0.10f, MatchThresholds.AUDIO.noBirdHintFloor)
    }

    @Test
    fun forSource_picksAudioForAudioSource() {
        val source =
            ScanSource.Audio(
                frameJpegPath = "",
                classification = Classification(emptyList(), 0L),
                audioWavPath = null,
            )
        assertEquals(MatchThresholds.AUDIO, MatchThresholds.forSource(source))
    }

    @Test
    fun forSource_picksPhotoForImageSource() {
        val source =
            ScanSource.Image(frameJpegPath = "", classification = Classification(emptyList(), 0L))
        assertEquals(MatchThresholds.PHOTO, MatchThresholds.forSource(source))
    }

    @Test
    fun photo_routeFor_at_exactly_match_threshold_routes_to_match() {
        assertEquals(MatchRoute.MATCH, MatchThresholds.PHOTO.routeFor(0.50f))
    }

    @Test
    fun photo_routeFor_just_below_match_threshold_routes_to_disambig() {
        assertEquals(MatchRoute.DISAMBIG, MatchThresholds.PHOTO.routeFor(0.4999f))
    }

    @Test
    fun photo_routeFor_just_below_disambig_threshold_routes_to_nobird() {
        assertEquals(MatchRoute.NOBIRD, MatchThresholds.PHOTO.routeFor(0.3499f))
    }

    @Test
    fun audio_routeFor_between_thresholds_routes_to_disambig() {
        assertEquals(MatchRoute.MATCH, MatchThresholds.AUDIO.routeFor(0.50f))
        assertEquals(MatchRoute.DISAMBIG, MatchThresholds.AUDIO.routeFor(0.45f))
        assertEquals(MatchRoute.DISAMBIG, MatchThresholds.AUDIO.routeFor(0.30f))
        assertEquals(MatchRoute.NOBIRD, MatchThresholds.AUDIO.routeFor(0.19f))
    }
}
