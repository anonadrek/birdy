package se.birdy.app.ui.match

import se.birdy.ml.ScanSource

/** Vilken UiState top-1-confidence ska route:a till. */
internal enum class MatchRoute { MATCH, DISAMBIG, NOBIRD }

/**
 * Källspecifika confidence-trösklar. PHOTO är de ursprungliga (Plan 7d).
 * AUDIO är sänkta INTERIMISTISKT (spec 2026-08-06): fotovärdena var aldrig
 * kalibrerade för ljud, och fält-inspelningar landar systematiskt lägre —
 * BirdNET:s eget default-golv är 0.1. Ersätts med evidens när xeno-canto-
 * evalen körs (follow-up).
 */
internal data class MatchThresholds(
    val matchConfidence: Float,
    val disambigConfidence: Float,
    val noBirdHintFloor: Float,
) {
    fun routeFor(topConfidence: Float): MatchRoute =
        when {
            topConfidence >= matchConfidence -> MatchRoute.MATCH
            topConfidence >= disambigConfidence -> MatchRoute.DISAMBIG
            else -> MatchRoute.NOBIRD
        }

    companion object {
        val PHOTO = MatchThresholds(matchConfidence = 0.50f, disambigConfidence = 0.35f, noBirdHintFloor = 0.15f)
        val AUDIO = MatchThresholds(matchConfidence = 0.40f, disambigConfidence = 0.20f, noBirdHintFloor = 0.10f)

        fun forSource(source: ScanSource): MatchThresholds = if (source is ScanSource.Audio) AUDIO else PHOTO
    }
}
