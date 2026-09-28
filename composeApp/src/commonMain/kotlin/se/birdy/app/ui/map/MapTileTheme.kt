package se.birdy.app.ui.map

/**
 * Field Journal map tint. The map uses MapTiler's grayscale "toner-v2" tiles; this duotone
 * ColorMatrix remaps luminance so black ink -> warm sepia [INK] and white -> paper [PAPER],
 * giving the ink-on-paper look without a custom MapTiler style. Constants tuned on device.
 */
object MapTileTheme {
    /**
     * Field Journal paper (MossCreme #F6EFE2 — the app's primary background). White tile
     * pixels map here so the map reads as the same "paper" as the screen it sits on (B7).
     */
    const val PAPER: Int = 0xF6EFE2

    /**
     * Warm dark sepia. Black tile pixels (ink lines, labels) map here. Bespoke, fine-tuned
     * on-device against the actual map tiles (coastlines, roads, labels) per the original
     * map-styling spec — never one of the reusable named ink tokens (checked against
     * TextOnCreme/MarginaliaInk/InkMuted/HeroMossDeep/BrassInk: no match, before or after
     * the 1.3.0 palette lift), so left unchanged in B7.
     */
    const val INK: Int = 0x2E2417

    /**
     * Builds a 4x5 ColorMatrix-compatible FloatArray (row-major, channels 0..255) that maps a
     * pixel's luminance L to `ink + L*(paper - ink)` per channel. Pure: no android.graphics types.
     */
    fun duotoneMatrix(
        ink: Int,
        paper: Int,
    ): FloatArray {
        val ir = (ink shr 16) and 0xFF
        val ig = (ink shr 8) and 0xFF
        val ib = ink and 0xFF
        val pr = (paper shr 16) and 0xFF
        val pg = (paper shr 8) and 0xFF
        val pb = paper and 0xFF

        // Rec.601 luma weights; they sum to 1.0 so white -> paper exactly.
        val lr = 0.299f
        val lg = 0.587f
        val lb = 0.114f

        val dr = (pr - ir) / 255f
        val dg = (pg - ig) / 255f
        val db = (pb - ib) / 255f

        // One matrix row per line — [luma->R, luma->G, luma->B, alpha, offset]. Assembled
        // row-wise (each floatArrayOf on a single line) so ktlint's argument-list-wrapping
        // rule is satisfied while the 4x5 shape stays readable.
        return floatArrayOf(lr * dr, lg * dr, lb * dr, 0f, ir.toFloat()) +
            floatArrayOf(lr * dg, lg * dg, lb * dg, 0f, ig.toFloat()) +
            floatArrayOf(lr * db, lg * db, lb * db, 0f, ib.toFloat()) +
            floatArrayOf(0f, 0f, 0f, 1f, 0f)
    }
}
