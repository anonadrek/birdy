package se.birdy.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Layout helpers for the Field Journal PDF. A4 portrait, 595×842pt. All drawing is plain
 * [Canvas]/[Paint] over [PdfDocument]. Colors mirror the in-app paper-and-ink palette.
 *
 * Page composition (in order):
 *  1. Title page
 *  2. Stats summary
 *  3..N Species spread (paginated via [JournalPageAggregator])
 *  N+1 Badges (only if any unlocked)
 *  Last: Colophon
 *
 * `PdfDocument` is a native AOSP class with no Robolectric backend, so this object is exercised
 * on-device only. Pure pagination logic lives in [JournalPageAggregator] (commonMain) where it
 * can be JVM-tested.
 */
internal object JournalPdfLayout {
    const val PAGE_W = 595
    const val PAGE_H = 842
    private const val MARGIN_X = 50f
    private const val MARGIN_TOP = 60f
    private const val MARGIN_BOTTOM = 60f

    private const val COLOR_PAPER_BG = 0xFFEFE7D6.toInt()
    private const val COLOR_PAPER_EDGE = 0xFFE5DCC7.toInt()
    private const val COLOR_INK = 0xFF3F4F30.toInt()
    private const val COLOR_COPPER = 0xFFA8552D.toInt()
    private const val COLOR_NAVY = 0xFF1F3A5F.toInt()

    fun drawTitlePage(
        doc: PdfDocument,
        input: JournalPdfInput,
        pageNum: Int,
    ) {
        val page = startPage(doc, pageNum)
        val canvas = page.canvas
        paintPaperBg(canvas)

        val year = yearOf(input.generatedAtMs)

        val ornamentPaint = caveatPaint(textSize = 16f, color = COLOR_COPPER, center = true)
        canvas.drawText("❦", PAGE_W / 2f, MARGIN_TOP + 30f, ornamentPaint)

        val titlePaint = dmSerifItalicPaint(textSize = 52f, color = COLOR_INK, center = true)
        canvas.drawText("Fältdagbok", PAGE_W / 2f, MARGIN_TOP + 150f, titlePaint)

        val subPaint = caveatPaint(textSize = 22f, color = COLOR_INK, center = true)
        canvas.drawText("av ${input.displayName}", PAGE_W / 2f, MARGIN_TOP + 188f, subPaint)

        val yearPaint = dmSerifItalicPaint(textSize = 28f, color = COLOR_COPPER, center = true)
        canvas.drawText("$year", PAGE_W / 2f, MARGIN_TOP + 240f, yearPaint)

        drawOrnamentRule(canvas, y = MARGIN_TOP + 280f)

        val teaserPaint = caveatPaint(textSize = 18f, color = COLOR_INK, center = true)
        val teaserText =
            "${input.stats.speciesSeenThisYear} arter sedda • " +
                "${input.stats.totalObservationsThisYear} fynd"
        canvas.drawText(teaserText, PAGE_W / 2f, MARGIN_TOP + 322f, teaserPaint)

        drawPageFooter(canvas, pageNum)
        doc.finishPage(page)
    }

    fun drawStatsPage(
        doc: PdfDocument,
        input: JournalPdfInput,
        pageNum: Int,
    ) {
        val page = startPage(doc, pageNum)
        val canvas = page.canvas
        paintPaperBg(canvas)

        drawSectionHeader(canvas, eyebrow = "Säsongens räkning", title = "${yearOf(input.generatedAtMs)} i siffror")

        val bigNumberPaint = dmSerifItalicPaint(textSize = 64f, color = COLOR_COPPER, center = false)
        val captionPaint = caveatPaint(textSize = 16f, color = COLOR_INK, center = false)
        val colW = (PAGE_W - 2 * MARGIN_X) / 2f

        val statsY = MARGIN_TOP + 200f
        canvas.drawText("${input.stats.speciesSeenThisYear}", MARGIN_X + 10f, statsY, bigNumberPaint)
        canvas.drawText("Arter i år", MARGIN_X + 10f, statsY + 22f, captionPaint)
        canvas.drawText("${input.stats.totalObservationsThisYear}", MARGIN_X + colW + 10f, statsY, bigNumberPaint)
        canvas.drawText("Totala fynd", MARGIN_X + colW + 10f, statsY + 22f, captionPaint)

        // Top species bar chart (max 5)
        if (input.stats.topSpecies.isNotEmpty()) {
            val chartTop = statsY + 80f
            val topsHeaderPaint = dmSerifItalicPaint(textSize = 22f, color = COLOR_INK, center = false)
            canvas.drawText("Topparter", MARGIN_X, chartTop, topsHeaderPaint)

            val barAreaX = MARGIN_X + 140f
            val barAreaW = PAGE_W - barAreaX - MARGIN_X
            val rowH = 28f
            val labelPaint = caveatPaint(textSize = 16f, color = COLOR_INK, center = false)
            val valuePaint = caveatPaint(textSize = 14f, color = COLOR_COPPER, center = false)
            val barPaint =
                Paint().apply {
                    color = COLOR_COPPER
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            val barBgPaint =
                Paint().apply {
                    color = COLOR_PAPER_EDGE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            val maxCount = (input.stats.topSpecies.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

            input.stats.topSpecies.take(5).forEachIndexed { i, (name, count) ->
                val y = chartTop + 24f + i * rowH
                canvas.drawText(name, MARGIN_X, y + 14f, labelPaint)
                val barY = y + 6f
                val barH = 14f
                canvas.drawRect(barAreaX, barY, barAreaX + barAreaW, barY + barH, barBgPaint)
                val filled = barAreaW * (count.toFloat() / maxCount)
                canvas.drawRect(barAreaX, barY, barAreaX + filled, barY + barH, barPaint)
                canvas.drawText("$count", barAreaX + filled + 6f, barY + 12f, valuePaint)
            }
        }

        drawPageFooter(canvas, pageNum)
        doc.finishPage(page)
    }

    fun drawSpeciesPage(
        doc: PdfDocument,
        input: JournalPdfInput,
        rows: List<JournalPageAggregator.SpeciesRow>,
        pageNum: Int,
        pageIndex: Int,
        totalSpeciesPages: Int,
    ) {
        val page = startPage(doc, pageNum)
        val canvas = page.canvas
        paintPaperBg(canvas)

        val eyebrow =
            if (totalSpeciesPages > 1) "Arter i fält (${pageIndex + 1}/$totalSpeciesPages)" else "Arter i fält"
        drawSectionHeader(canvas, eyebrow = eyebrow, title = "Det jag sett")

        val rowTop = MARGIN_TOP + 170f
        val rowH = 24f
        val namePaint = dmSerifItalicPaint(textSize = 14f, color = COLOR_INK, center = false)
        val sciPaint = caveatPaint(textSize = 12f, color = COLOR_INK, center = false)
        val countPaint = dmSerifItalicPaint(textSize = 14f, color = COLOR_COPPER, center = false)
        val datePaint = caveatPaint(textSize = 11f, color = COLOR_INK, center = false)
        val thumbPaint =
            Paint().apply {
                color = COLOR_PAPER_EDGE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        val rulePaint =
            Paint().apply {
                color = COLOR_PAPER_EDGE
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
                isAntiAlias = true
            }

        rows.forEachIndexed { i, row ->
            val y = rowTop + i * rowH

            // Thumbnail-placeholder rect
            val thumbRect = RectF(MARGIN_X, y - 12f, MARGIN_X + 18f, y + 6f)
            canvas.drawRect(thumbRect, thumbPaint)

            canvas.drawText(row.nameLocalized, MARGIN_X + 26f, y, namePaint)
            if (row.scientificName.isNotEmpty()) {
                canvas.drawText(row.scientificName, MARGIN_X + 26f, y + 12f, sciPaint)
            }

            val countText = "${row.count} fynd"
            canvas.drawText(countText, PAGE_W - MARGIN_X - 120f, y, countPaint)
            canvas.drawText("Först: ${formatDate(row.firstSeenMs)}", PAGE_W - MARGIN_X - 120f, y + 12f, datePaint)

            // Hairline rule
            canvas.drawLine(MARGIN_X, y + 14f, PAGE_W - MARGIN_X, y + 14f, rulePaint)
        }

        drawPageFooter(canvas, pageNum)
        doc.finishPage(page)
    }

    fun drawBadgesPage(
        doc: PdfDocument,
        input: JournalPdfInput,
        pageNum: Int,
    ) {
        val page = startPage(doc, pageNum)
        val canvas = page.canvas
        paintPaperBg(canvas)

        drawSectionHeader(canvas, eyebrow = "Märken jag tjänat", title = "Stämplar i marginalen")

        val rowTop = MARGIN_TOP + 180f
        val rowH = 48f
        val namePaint = dmSerifItalicPaint(textSize = 18f, color = COLOR_INK, center = false)
        val descPaint = caveatPaint(textSize = 14f, color = COLOR_INK, center = false)
        val datePaint = caveatPaint(textSize = 12f, color = COLOR_COPPER, center = false)
        val stampPaint =
            Paint().apply {
                color = COLOR_NAVY
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                isAntiAlias = true
            }

        input.unlockedPremiumBadges.take(10).forEachIndexed { i, badge ->
            val y = rowTop + i * rowH
            // Stamp circle
            canvas.drawCircle(MARGIN_X + 16f, y + 6f, 14f, stampPaint)
            canvas.drawText(badge.nameLocalized, MARGIN_X + 44f, y, namePaint)
            canvas.drawText(badge.descriptionLocalized, MARGIN_X + 44f, y + 16f, descPaint)
            canvas.drawText(
                formatDate(badge.unlockedAt.toEpochMilliseconds()),
                PAGE_W - MARGIN_X - 90f,
                y + 4f,
                datePaint,
            )
        }

        drawPageFooter(canvas, pageNum)
        doc.finishPage(page)
    }

    fun drawColophonPage(
        doc: PdfDocument,
        input: JournalPdfInput,
        pageNum: Int,
    ) {
        val page = startPage(doc, pageNum)
        val canvas = page.canvas
        paintPaperBg(canvas)

        drawOrnamentRule(canvas, y = PAGE_H / 2f - 50f)

        val mark = dmSerifItalicPaint(textSize = 26f, color = COLOR_INK, center = true)
        canvas.drawText("Birdy Bird Scanner", PAGE_W / 2f, PAGE_H / 2f, mark)

        val genPaint = caveatPaint(textSize = 14f, color = COLOR_INK, center = true)
        canvas.drawText(
            "Genererad ${formatDateTime(input.generatedAtMs)}",
            PAGE_W / 2f,
            PAGE_H / 2f + 28f,
            genPaint,
        )

        drawOrnamentRule(canvas, y = PAGE_H / 2f + 70f)

        drawPageFooter(canvas, pageNum)
        doc.finishPage(page)
    }

    // ----- helpers ---------------------------------------------------------

    private fun startPage(
        doc: PdfDocument,
        pageNum: Int,
    ): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
        return doc.startPage(info)
    }

    private fun paintPaperBg(canvas: Canvas) {
        val paint =
            Paint().apply {
                color = COLOR_PAPER_BG
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), PAGE_H.toFloat(), paint)
    }

    private fun drawSectionHeader(
        canvas: Canvas,
        eyebrow: String,
        title: String,
    ) {
        val eyebrowPaint = caveatPaint(textSize = 18f, color = COLOR_COPPER, center = true)
        canvas.drawText(eyebrow, PAGE_W / 2f, MARGIN_TOP + 50f, eyebrowPaint)

        val titlePaint = dmSerifItalicPaint(textSize = 36f, color = COLOR_INK, center = true)
        canvas.drawText(title, PAGE_W / 2f, MARGIN_TOP + 100f, titlePaint)

        drawOrnamentRule(canvas, y = MARGIN_TOP + 130f)
    }

    private fun drawOrnamentRule(
        canvas: Canvas,
        y: Float,
    ) {
        val rulePaint =
            Paint().apply {
                color = COLOR_INK
                style = Paint.Style.STROKE
                strokeWidth = 0.6f
                isAntiAlias = true
            }
        val centerOrnamentPaint = caveatPaint(textSize = 14f, color = COLOR_COPPER, center = true)
        val rulePadding = 30f
        val leftEnd = PAGE_W / 2f - rulePadding
        val rightStart = PAGE_W / 2f + rulePadding
        canvas.drawLine(MARGIN_X + 40f, y, leftEnd, y, rulePaint)
        canvas.drawLine(rightStart, y, PAGE_W - MARGIN_X - 40f, y, rulePaint)
        canvas.drawText("❦", PAGE_W / 2f, y + 5f, centerOrnamentPaint)
    }

    private fun drawPageFooter(
        canvas: Canvas,
        pageNum: Int,
    ) {
        val pageMarkPaint = caveatPaint(textSize = 12f, color = COLOR_INK, center = true)
        canvas.drawText("— $pageNum —", PAGE_W / 2f, PAGE_H - MARGIN_BOTTOM / 2f, pageMarkPaint)
    }

    private fun dmSerifItalicPaint(
        textSize: Float,
        color: Int,
        center: Boolean,
    ): Paint =
        Paint().apply {
            this.color = color
            this.textSize = textSize
            typeface = PdfFontProvider.dmSerifItalic()
            isAntiAlias = true
            if (center) textAlign = Paint.Align.CENTER
        }

    private fun caveatPaint(
        textSize: Float,
        color: Int,
        center: Boolean,
    ): Paint =
        Paint().apply {
            this.color = color
            this.textSize = textSize
            typeface = PdfFontProvider.caveat()
            isAntiAlias = true
            if (center) textAlign = Paint.Align.CENTER
        }

    private fun yearOf(epochMs: Long): Int = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).year

    private fun formatDate(epochMs: Long): String {
        val ldt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
        val mm = ldt.monthNumber.toString().padStart(2, '0')
        val dd = ldt.dayOfMonth.toString().padStart(2, '0')
        return "${ldt.year}-$mm-$dd"
    }

    private fun formatDateTime(epochMs: Long): String {
        val ldt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
        val mm = ldt.monthNumber.toString().padStart(2, '0')
        val dd = ldt.dayOfMonth.toString().padStart(2, '0')
        val hh = ldt.hour.toString().padStart(2, '0')
        val mi = ldt.minute.toString().padStart(2, '0')
        return "${ldt.year}-$mm-$dd $hh:$mi"
    }
}
