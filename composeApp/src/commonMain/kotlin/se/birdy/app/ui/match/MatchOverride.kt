package se.birdy.app.ui.match

/**
 * DEBUG-only override for MatchResultViewModel routing. Pushed via:
 *   adb push override.txt /data/data/se.birdy.android/files/match_override.txt
 * See docs/superpowers/runbooks/2026-05-16-test-image-infra.md.
 */
data class MatchOverride(
    val qid: String,
    val confidence: Float,
)

private val QID_REGEX = Regex("^Q\\d+$")

fun parseMatchOverride(text: String): MatchOverride? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val qid = parts[0].trim()
    val confStr = parts[1].trim()
    if (!QID_REGEX.matches(qid)) return null
    val conf = confStr.toFloatOrNull() ?: return null
    if (conf !in 0.0f..1.0f) return null
    return MatchOverride(qid, conf)
}
