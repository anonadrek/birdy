package se.birdy.app.ui.match

/** Vilken UiState top-1-confidence ska route:a till. */
internal enum class MatchRoute { MATCH, DISAMBIG, NOBIRD }

internal object MatchThresholds {
    /** Top-1 conf ≥ → Match-skärm. */
    const val MATCH_CONFIDENCE = 0.50f

    /** Top-1 conf ≥ (men < MATCH_CONFIDENCE) → Disambig-skärm. < → NoBird. */
    const val DISAMBIG_CONFIDENCE = 0.35f

    fun routeFor(topConfidence: Float): MatchRoute =
        when {
            topConfidence >= MATCH_CONFIDENCE -> MatchRoute.MATCH
            topConfidence >= DISAMBIG_CONFIDENCE -> MatchRoute.DISAMBIG
            else -> MatchRoute.NOBIRD
        }
}
