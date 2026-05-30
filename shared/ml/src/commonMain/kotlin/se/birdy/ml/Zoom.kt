package se.birdy.ml

/**
 * Snapshot av kamerans zoom-läge. minRatio är 1f på alla kameror vi stödjer;
 * maxRatio är enhetens faktiska tak (kan vara < eller > UI-takets 10x).
 */
data class ZoomState(
    val ratio: Float,
    val minRatio: Float,
    val maxRatio: Float,
) {
    companion object {
        val NONE = ZoomState(ratio = 1f, minRatio = 1f, maxRatio = 1f)
    }
}

private val BASE_PRESETS = listOf(1f, 2f, 5f, 10f)

/**
 * Vilka zoom-chips som ska visas givet enhetens maxRatio.
 * - Tar baspresets (1/2/5/10) som är < maxRatio.
 * - Lägger till min(10, maxRatio) som översta chip (taket), om det inte redan finns.
 * - Returnerar tom lista om kameran saknar zoom (maxRatio <= 1).
 */
fun zoomPresets(maxRatio: Float): List<Float> {
    if (maxRatio <= 1f) return emptyList()
    val top = minOf(10f, maxRatio)
    val below = BASE_PRESETS.filter { it < top }
    return if (below.lastOrNull() == top) below else below + top
}
