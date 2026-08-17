package se.birdy.pdf

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Delad geometri, palett, typstorlekar, strängar och datumformatterare för Fältdagbok-PDF:en.
 * Android ritar med android.graphics.pdf.PdfDocument ([JournalPdfLayout]), iOS med
 * UIGraphicsPDFRenderer/CoreGraphics — BÅDA MÅSTE läsa alla värden härifrån så plattformarna
 * inte divergerar. Geometri + typstorlekar i pt (PDF-punkter), färger som ARGB Long.
 *
 * Strängarna är avsiktligt olokaliserade svenska — PDF:en är en Field Journal-artefakt (fysiskt
 * "papper"), inte lokaliserat app-UI. `_FMT`-strängarna innehåller `%s`-platshållare avsedda för
 * [fmt], inte `String.format` (Kotlin/Native saknar en gemensam `String.format`-implementation).
 */
object JournalPdfMetrics {
    // ----- Geometri (pt) -----------------------------------------------------
    const val PAGE_W = 595
    const val PAGE_H = 842
    const val MARGIN_X: Float = 50f
    const val MARGIN_TOP: Float = 60f
    const val MARGIN_BOTTOM: Float = 60f

    // ----- Palett (ARGB Long) -------------------------------------------------
    const val COLOR_PAPER_BG: Long = 0xFFEFE7D6
    const val COLOR_PAPER_EDGE: Long = 0xFFE5DCC7
    const val COLOR_INK: Long = 0xFF3F4F30
    const val COLOR_COPPER: Long = 0xFFA8552D
    const val COLOR_NAVY: Long = 0xFF1F3A5F

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

    // ----- Strängar (svenska, avsiktligt olokaliserade) ------------------------
    const val TITLE = "Fältdagbok"
    const val BY_FMT = "av %s"
    const val TEASER_FMT = "%s arter sedda • %s fynd"
    const val STATS_EYEBROW = "Säsongens räkning"
    const val STATS_TITLE_FMT = "%s i siffror"
    const val STAT_SPECIES = "Arter i år"
    const val STAT_TOTAL = "Totala fynd"
    const val TOPS = "Topparter"
    const val SPECIES_EYEBROW = "Arter i fält"
    const val SPECIES_EYEBROW_PAGED_FMT = "Arter i fält (%s/%s)"
    const val SPECIES_TITLE = "Det jag sett"
    const val COUNT_FMT = "%s fynd"
    const val FIRST_FMT = "Först: %s"
    const val BADGES_EYEBROW = "Märken jag tjänat"
    const val BADGES_TITLE = "Stämplar i marginalen"
    const val COLOPHON = "Birdy Bird Scanner"
    const val GENERATED_FMT = "Genererad %s"
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
