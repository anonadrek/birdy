package se.birdy.pdf

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Delad geometri, palett, typstorlekar och datumformatterare för Fältdagbok-PDF:en. Android ritar
 * med android.graphics.pdf.PdfDocument ([JournalPdfLayout]), iOS med
 * UIGraphicsPDFRenderer/CoreGraphics — BÅDA MÅSTE läsa alla värden härifrån så plattformarna
 * inte divergerar. Geometri + typstorlekar i pt (PDF-punkter), färger som ARGB Long.
 *
 * De lokaliserade strängarna (titel, rubriker, etc.) flyttade till [JournalPdfStrings] när PDF:en
 * fick engelska (bugg: en engelsk användares export fick svenska rubriker). Kvar här är bara det
 * som INTE är per-språk-text: [COLOPHON] (varumärkesnamnet), [FOOTER_FMT] (bara ett sidnummer
 * mellan tankstreck) och [ORNAMENT_GLYPH] (en glyf) — ingen av de tre är faktiskt översättningsbar
 * text. `fmt`-hjälparen tar `%s`-platshållare, inte `String.format` (Kotlin/Native saknar en
 * gemensam `String.format`-implementation); [JournalPdfStrings] använder samma hjälpare.
 */
object JournalPdfMetrics {
    // ----- Geometri (pt) -----------------------------------------------------
    const val PAGE_W = 595
    const val PAGE_H = 842
    const val MARGIN_X: Float = 50f
    const val MARGIN_TOP: Float = 60f
    const val MARGIN_BOTTOM: Float = 60f

    /** Topparternas staplar börjar så här långt in från MARGIN_X; etiketterna står i kolumnen före. */
    const val TOPS_BAR_OFFSET: Float = 140f

    /** Luft mellan en etikett och stapeln, så att etiketten aldrig rör stapeln. */
    const val TOPS_LABEL_GAP: Float = 8f

    /** Minsta skala för en lång etikett innan den kortas med "…" ([fitLabel]). */
    const val LABEL_MIN_SCALE: Float = 0.75f

    // ----- Palett (ARGB Long) — Field Journal "Mossa, rost & mässing" (1.3.0) -------------------
    const val COLOR_PAPER_BG: Long = 0xFFF6EFE2 // MossCreme
    const val COLOR_PAPER_EDGE: Long = 0xFFDFD2BA // Hairline
    const val COLOR_INK: Long = 0xFF26301F // TextOnCreme
    const val COLOR_COPPER: Long = 0xFF9A4526 // AccentCopper
    const val COLOR_NAVY: Long = 0xFF1F3A5F // StampNavy (unchanged in 1.3.0)

    // ----- Typstorlekar (pt) ---------------------------------------------------
    const val TITLE_SIZE: Float = 52f
    const val TITLE_SUB: Float = 22f
    const val TITLE_YEAR: Float = 28f
    const val TITLE_TEASER: Float = 18f
    const val ORNAMENT: Float = 14f
    const val ORNAMENT_TOP: Float = 16f
    const val STAT_NUMBER: Float = 64f
    const val STAT_CAPTION: Float = 16f
    const val TOPS_HEADER: Float = 22f
    const val BAR_LABEL: Float = 16f
    const val BAR_VALUE: Float = 14f
    const val SPECIES_NAME: Float = 14f
    const val SPECIES_SCI: Float = 12f
    const val SPECIES_COUNT: Float = 14f
    const val SPECIES_DATE: Float = 11f
    const val BADGE_NAME: Float = 18f
    const val BADGE_DESC: Float = 14f
    const val BADGE_DATE: Float = 12f
    const val COLOPHON_MARK: Float = 26f
    const val COLOPHON_GEN: Float = 14f
    const val FOOTER: Float = 12f
    const val SECTION_EYEBROW: Float = 18f
    const val SECTION_TITLE: Float = 36f

    // ----- Strängar som INTE är per-språk-text (se klass-KDoc) ------------------
    const val COLOPHON = "Birdy Bird Scanner"
    const val FOOTER_FMT = "— %s —"
    const val ORNAMENT_GLYPH = "❦"

    // ----- Formatterare ----------------------------------------------------------

    /** Kalenderåret för [epochMs] i [zone]. */
    fun yearOf(
        epochMs: Long,
        zone: TimeZone,
    ): Int = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).year

    /** `"yyyy-MM-dd"` för [epochMs] i [zone]. */
    fun formatDate(
        epochMs: Long,
        zone: TimeZone,
    ): String {
        val ldt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
        val mm = ldt.monthNumber.toString().padStart(2, '0')
        val dd = ldt.dayOfMonth.toString().padStart(2, '0')
        return "${ldt.year}-$mm-$dd"
    }

    /** `"yyyy-MM-dd HH:mm"` för [epochMs] i [zone]. */
    fun formatDateTime(
        epochMs: Long,
        zone: TimeZone,
    ): String {
        val ldt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
        val mm = ldt.monthNumber.toString().padStart(2, '0')
        val dd = ldt.dayOfMonth.toString().padStart(2, '0')
        val hh = ldt.hour.toString().padStart(2, '0')
        val mi = ldt.minute.toString().padStart(2, '0')
        return "${ldt.year}-$mm-$dd $hh:$mi"
    }

    /** Ersätter `%s` i [pattern] med [args] i ordning. Enda platshållaren som stöds är `%s`. */
    fun fmt(
        pattern: String,
        vararg args: String,
    ): String {
        var result = pattern
        for (arg in args) {
            result = result.replaceFirst("%s", arg)
        }
        return result
    }
}
