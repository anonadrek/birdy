package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.map.MapMarkerSpec
import se.birdy.app.ui.map.MapTileTheme
import se.birdy.pdf.JournalPdfMetrics
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Some modules can't import these Color tokens directly and keep their own raw ARGB Long/Int
 * copy of the same hex values instead: [MapMarkerSpec] and [MapTileTheme] are shared with the
 * platform-specific `android.graphics`/CoreGraphics drawing code (which doesn't take Compose
 * `Color`), and [JournalPdfMetrics] lives in `:shared:pdf`, which doesn't depend on this theme
 * module. This is the tripwire: if the palette ever moves again and one of these copies isn't
 * updated in lockstep, it fails here instead of silently drifting (fix wave B8, finding 3).
 */
class PaletteMirrorTest {
    @Test
    fun `MapMarkerSpec mirrors the theme tokens`() {
        assertEquals(PaperTop, Color(MapMarkerSpec.CREAM_HI))
        assertEquals(SandCreme, Color(MapMarkerSpec.CREAM_LO))
        assertEquals(AccentCopper, Color(MapMarkerSpec.COPPER))
    }

    @Test
    fun `MapTileTheme's paper anchor mirrors MossCreme`() {
        // PAPER is a bare 24-bit RGB Int (no alpha channel) — OR in an opaque alpha byte
        // before constructing a Color, mirroring how the ARGB Long/Int constants above do.
        assertEquals(MossCreme, Color(MapTileTheme.PAPER or 0xFF000000.toInt()))
    }

    @Test
    fun `JournalPdfMetrics mirrors the theme tokens`() {
        assertEquals(TextOnCreme, Color(JournalPdfMetrics.COLOR_INK))
        assertEquals(AccentCopper, Color(JournalPdfMetrics.COLOR_COPPER))
        assertEquals(MossCreme, Color(JournalPdfMetrics.COLOR_PAPER_BG))
        assertEquals(Hairline, Color(JournalPdfMetrics.COLOR_PAPER_EDGE))
        assertEquals(StampNavy, Color(JournalPdfMetrics.COLOR_NAVY))
    }
}
