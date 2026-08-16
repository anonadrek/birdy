package se.birdy.app.ui.map

/**
 * Delad geometri + palett för fynd-markören (cream vaxsigill-disc, koppar-ring,
 * navy-tintad Birdy-fågel, nedåtpekande spets vars topp markerar fyndet).
 * Android ritar med android.graphics (MapMarkerIcon.android.kt), iOS med CoreGraphics —
 * båda MÅSTE läsa härifrån så plattformarna inte divergerar. Mått i dp/pt.
 */
object MapMarkerSpec {
    const val COPPER: Long = 0xFFA8552D // AccentCopper
    const val NAVY: Long = 0xFF1F3A5F // StampNavy
    const val CREAM_HI: Long = 0xFFF4EDDC
    const val CREAM_LO: Long = 0xFFE5DBC4
    const val SHADOW: Long = 0x66281910

    const val RING_WIDTH: Float = 3f
    const val DISC_DIAMETER: Float = 46f
    const val POINT_HEIGHT: Float = 9f
    const val PADDING: Float = 3f // luft för drop-skuggan
    const val POINT_HALF_WIDTH: Float = 7f
    const val POINT_TOP_INSET: Float = 2f // spetsens bas ligger 2dp in i discen

    const val SHADOW_BLUR: Float = 2f
    const val SHADOW_DY: Float = 1f

    /** RadialGradient-parametrar relativt disc-radien r: center (cx - r*CX, cy - r*CY), radie r*RADIUS. */
    const val GRADIENT_CX_OFFSET: Float = 0.25f
    const val GRADIENT_CY_OFFSET: Float = 0.3f
    const val GRADIENT_RADIUS: Float = 1.3f

    /** Fågelsilhuettens långsida som andel av disc-diametern. */
    const val BIRD_FRACTION: Float = 0.6f

    /** Markör-bitmapens totalmått (dp/pt) — BÅDA plattformarnas canvas-storlek räknas härifrån. */
    fun markerWidth(): Float = DISC_DIAMETER + PADDING * 2

    fun markerHeight(): Float = DISC_DIAMETER + POINT_HEIGHT + PADDING * 2
}
