package se.birdy.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import kotlinx.datetime.TimeZone

/**
 * Layout helpers for the Field Journal PDF. A4 portrait, 595×842pt. All drawing is plain
 * [Canvas]/[Paint] over [PdfDocument]. Geometry, palette, type sizes and strings all come from
 * [JournalPdfMetrics] (commonMain) — this object is pure indirection over those values plus the
 * Android-only [Paint]/[Canvas] drawing calls, so the iOS renderer (CoreGraphics) can reproduce
 * the same layout from the same source of truth without duplicating a single literal.
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
    fun drawTitlePage(
        doc: PdfDocument,
        input: JournalPdfInput,
        pageNum: Int,
    ) {
        val page = startPage(doc, pageNum)
        val canvas = page.canvas
        paintPaperBg(canvas)

        val year = JournalPdfMetrics.yearOf(input.generatedAtMs, TimeZone.currentSystemDefault())

        val ornamentPaint =
            caveatPaint(textSize = JournalPdfMetrics.ORNAMENT_TOP, color = JournalPdfMetrics.COLOR_COPPER.toInt(), center = true)
        canvas.drawText(JournalPdfMetrics.ORNAMENT_GLYPH, JournalPdfMetrics.PAGE_W / 2f, JournalPdfMetrics.MARGIN_TOP + 30f, ornamentPaint)

        val titlePaint =
            dmSerifItalicPaint(textSize = JournalPdfMetrics.TITLE_SIZE, color = JournalPdfMetrics.COLOR_INK.toInt(), center = true)
        canvas.drawText(JournalPdfMetrics.TITLE, JournalPdfMetrics.PAGE_W / 2f, JournalPdfMetrics.MARGIN_TOP + 150f, titlePaint)

        val subPaint = caveatPaint(textSize = JournalPdfMetrics.TITLE_SUB, color = JournalPdfMetrics.COLOR_INK.toInt(), center = true)
        val byline = JournalPdfMetrics.fmt(JournalPdfMetrics.BY_FMT, input.displayName)
        canvas.drawText(byline, JournalPdfMetrics.PAGE_W / 2f, JournalPdfMetrics.MARGIN_TOP + 188f, subPaint)

        val yearPaint =
            dmSerifItalicPaint(textSize = JournalPdfMetrics.TITLE_YEAR, color = JournalPdfMetrics.COLOR_COPPER.toInt(), center = true)
        canvas.drawText("$year", JournalPdfMetrics.PAGE_W / 2f, JournalPdfMetrics.MARGIN_TOP + 240f, yearPaint)

        drawOrnamentRule(canvas, y = JournalPdfMetrics.MARGIN_TOP + 280f)

        val teaserPaint = caveatPaint(textSize = JournalPdfMetrics.TITLE_TEASER, color = JournalPdfMetrics.COLOR_INK.toInt(), center = true)
        val teaserText =
            JournalPdfMetrics.fmt(
                JournalPdfMetrics.TEASER_FMT,
                "${input.stats.speciesSeenThisYear}",
                "${input.stats.totalObservationsThisYear}",
            )
        canvas.drawText(teaserText, JournalPdfMetrics.PAGE_W / 2f, JournalPdfMetrics.MARGIN_TOP + 322f, teaserPaint)

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

        val year = JournalPdfMetrics.yearOf(input.generatedAtMs, TimeZone.currentSystemDefault())
        drawSectionHeader(
            canvas,
            eyebrow = JournalPdfMetrics.STATS_EYEBROW,
            title = JournalPdfMetrics.fmt(JournalPdfMetrics.STATS_TITLE_FMT, "$year"),
        )

        val bigNumberPaint =
            dmSerifItalicPaint(textSize = JournalPdfMetrics.STAT_NUMBER, color = JournalPdfMetrics.COLOR_COPPER.toInt(), center = false)
        val captionPaint =
            caveatPaint(textSize = JournalPdfMetrics.STAT_CAPTION, color = JournalPdfMetrics.COLOR_INK.toInt(), center = false)
        val colW = (JournalPdfMetrics.PAGE_W - 2 * JournalPdfMetrics.MARGIN_X) / 2f

        val statsY = JournalPdfMetrics.MARGIN_TOP + 200f
        canvas.drawText("${input.stats.speciesSeenThisYear}", JournalPdfMetrics.MARGIN_X + 10f, statsY, bigNumberPaint)
        canvas.drawText(JournalPdfMetrics.STAT_SPECIES, JournalPdfMetrics.MARGIN_X + 10f, statsY + 22f, captionPaint)
        canvas.drawText("${input.stats.totalObservationsThisYear}", JournalPdfMetrics.MARGIN_X + colW + 10f, statsY, bigNumberPaint)
        canvas.drawText(JournalPdfMetrics.STAT_TOTAL, JournalPdfMetrics.MARGIN_X + colW + 10f, statsY + 22f, captionPaint)

        // Top species bar chart (max 5)
        if (input.stats.topSpecies.isNotEmpty()) {
            val chartTop = statsY + 80f
            val topsHeaderPaint =
                dmSerifItalicPaint(textSize = JournalPdfMetrics.TOPS_HEADER, color = JournalPdfMetrics.COLOR_INK.toInt(), center = false)
            canvas.drawText(JournalPdfMetrics.TOPS, JournalPdfMetrics.MARGIN_X, chartTop, topsHeaderPaint)

            val barAreaX = JournalPdfMetrics.MARGIN_X + 140f
            val barAreaW = JournalPdfMetrics.PAGE_W - barAreaX - JournalPdfMetrics.MARGIN_X
            val rowH = 28f
            val labelPaint =
                caveatPaint(textSize = JournalPdfMetrics.BAR_LABEL, color = JournalPdfMetrics.COLOR_INK.toInt(), center = false)
            val valuePaint =
                caveatPaint(textSize = JournalPdfMetrics.BAR_VALUE, color = JournalPdfMetrics.COLOR_COPPER.toInt(), center = false)
            val barPaint =
                Paint().apply {
                    color = JournalPdfMetrics.COLOR_COPPER.toInt()
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            val barBgPaint =
                Paint().apply {
                    color = JournalPdfMetrics.COLOR_PAPER_EDGE.toInt()
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            val maxCount = (input.stats.topSpecies.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

            input.stats.topSpecies.take(5).forEachIndexed { i, (name, count) ->
                val y = chartTop + 24f + i * rowH
                canvas.drawText(name, JournalPdfMetrics.MARGIN_X, y + 14f, labelPaint)
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
            if (totalSpeciesPages > 1) {
                JournalPdfMetrics.fmt(JournalPdfMetrics.SPECIES_EYEBROW_PAGED_FMT, "${pageIndex + 1}", "$totalSpeciesPages")
            } else {
                JournalPdfMetrics.SPECIES_EYEBROW
            }
        drawSectionHeader(canvas, eyebrow = eyebrow, title = JournalPdfMetrics.SPECIES_TITLE)

        val rowTop = JournalPdfMetrics.MARGIN_TOP + 170f
        val rowH = 24f
        val namePaint =
            dmSerifItalicPaint(textSize = JournalPdfMetrics.SPECIES_NAME, color = JournalPdfMetrics.COLOR_INK.toInt(), center = false)
        val sciPaint = caveatPaint(textSize = JournalPdfMetrics.SPECIES_SCI, color = JournalPdfMetrics.COLOR_INK.toInt(), center = false)
        val countPaint =
            dmSerifItalicPaint(textSize = JournalPdfMetrics.SPECIES_COUNT, color = JournalPdfMetrics.COLOR_COPPER.toInt(), center = false)
        val datePaint = caveatPaint(textSize = JournalPdfMetrics.SPECIES_DATE, color = JournalPdfMetrics.COLOR_INK.toInt(), center = false)
        val thumbPaint =
            Paint().apply {
                color = JournalPdfMetrics.COLOR_PAPER_EDGE.toInt()
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        val rulePaint =
            Paint().apply {
                color = JournalPdfMetrics.COLOR_PAPER_EDGE.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 0.5f
                isAntiAlias = true
            }

        rows.forEachIndexed { i, row ->
            val y = rowTop + i * rowH

            // Thumbnail-placeholder rect
            val thumbRect = RectF(JournalPdfMetrics.MARGIN_X, y - 12f, JournalPdfMetrics.MARGIN_X + 18f, y + 6f)
            canvas.drawRect(thumbRect, thumbPaint)

            canvas.drawText(row.nameLocalized, JournalPdfMetrics.MARGIN_X + 26f, y, namePaint)
            if (row.scientificName.isNotEmpty()) {
                canvas.drawText(row.scientificName, JournalPdfMetrics.MARGIN_X + 26f, y + 12f, sciPaint)
            }

            val countText = JournalPdfMetrics.fmt(JournalPdfMetrics.COUNT_FMT, "${row.count}")
            val firstSeenDate = JournalPdfMetrics.formatDate(row.firstSeenMs, TimeZone.currentSystemDefault())
            val firstSeenText = JournalPdfMetrics.fmt(JournalPdfMetrics.FIRST_FMT, firstSeenDate)
            val rightX = JournalPdfMetrics.PAGE_W - JournalPdfMetrics.MARGIN_X - 120f
            canvas.drawText(countText, rightX, y, countPaint)
            canvas.drawText(firstSeenText, rightX, y + 12f, datePaint)

            // Hairline rule
            canvas.drawLine(JournalPdfMetrics.MARGIN_X, y + 14f, JournalPdfMetrics.PAGE_W - JournalPdfMetrics.MARGIN_X, y + 14f, rulePaint)
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

        drawSectionHeader(canvas, eyebrow = JournalPdfMetrics.BADGES_EYEBROW, title = JournalPdfMetrics.BADGES_TITLE)

        val rowTop = JournalPdfMetrics.MARGIN_TOP + 180f
        val rowH = 48f
        val namePaint =
            dmSerifItalicPaint(textSize = JournalPdfMetrics.BADGE_NAME, color = JournalPdfMetrics.COLOR_INK.toInt(), center = false)
        val descPaint = caveatPaint(textSize = JournalPdfMetrics.BADGE_DESC, color = JournalPdfMetrics.COLOR_INK.toInt(), center = false)
        val datePaint = caveatPaint(textSize = JournalPdfMetrics.BADGE_DATE, color = JournalPdfMetrics.COLOR_COPPER.toInt(), center = false)
        val stampPaint =
            Paint().apply {
                color = JournalPdfMetrics.COLOR_NAVY.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                isAntiAlias = true
            }

        input.unlockedPremiumBadges.take(10).forEachIndexed { i, badge ->
            val y = rowTop + i * rowH
            // Stamp circle
            canvas.drawCircle(JournalPdfMetrics.MARGIN_X + 16f, y + 6f, 14f, stampPaint)
            canvas.drawText(badge.nameLocalized, JournalPdfMetrics.MARGIN_X + 44f, y, namePaint)
            canvas.drawText(badge.descriptionLocalized, JournalPdfMetrics.MARGIN_X + 44f, y + 16f, descPaint)
            canvas.drawText(
                JournalPdfMetrics.formatDate(badge.unlockedAt.toEpochMilliseconds(), TimeZone.currentSystemDefault()),
                JournalPdfMetrics.PAGE_W - JournalPdfMetrics.MARGIN_X - 90f,
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

        drawOrnamentRule(canvas, y = JournalPdfMetrics.PAGE_H / 2f - 50f)

        val mark =
            dmSerifItalicPaint(textSize = JournalPdfMetrics.COLOPHON_MARK, color = JournalPdfMetrics.COLOR_INK.toInt(), center = true)
        canvas.drawText(JournalPdfMetrics.COLOPHON, JournalPdfMetrics.PAGE_W / 2f, JournalPdfMetrics.PAGE_H / 2f, mark)

        val genPaint = caveatPaint(textSize = JournalPdfMetrics.COLOPHON_GEN, color = JournalPdfMetrics.COLOR_INK.toInt(), center = true)
        val generatedAt = JournalPdfMetrics.formatDateTime(input.generatedAtMs, TimeZone.currentSystemDefault())
        canvas.drawText(
            JournalPdfMetrics.fmt(JournalPdfMetrics.GENERATED_FMT, generatedAt),
            JournalPdfMetrics.PAGE_W / 2f,
            JournalPdfMetrics.PAGE_H / 2f + 28f,
            genPaint,
        )

        drawOrnamentRule(canvas, y = JournalPdfMetrics.PAGE_H / 2f + 70f)

        drawPageFooter(canvas, pageNum)
        doc.finishPage(page)
    }

    // ----- helpers ---------------------------------------------------------

    private fun startPage(
        doc: PdfDocument,
        pageNum: Int,
    ): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(JournalPdfMetrics.PAGE_W, JournalPdfMetrics.PAGE_H, pageNum).create()
        return doc.startPage(info)
    }

    private fun paintPaperBg(canvas: Canvas) {
        val paint =
            Paint().apply {
                color = JournalPdfMetrics.COLOR_PAPER_BG.toInt()
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        canvas.drawRect(0f, 0f, JournalPdfMetrics.PAGE_W.toFloat(), JournalPdfMetrics.PAGE_H.toFloat(), paint)
    }

    private fun drawSectionHeader(
        canvas: Canvas,
        eyebrow: String,
        title: String,
    ) {
        val eyebrowPaint =
            caveatPaint(textSize = JournalPdfMetrics.SECTION_EYEBROW, color = JournalPdfMetrics.COLOR_COPPER.toInt(), center = true)
        canvas.drawText(eyebrow, JournalPdfMetrics.PAGE_W / 2f, JournalPdfMetrics.MARGIN_TOP + 50f, eyebrowPaint)

        val titlePaint =
            dmSerifItalicPaint(textSize = JournalPdfMetrics.SECTION_TITLE, color = JournalPdfMetrics.COLOR_INK.toInt(), center = true)
        canvas.drawText(title, JournalPdfMetrics.PAGE_W / 2f, JournalPdfMetrics.MARGIN_TOP + 100f, titlePaint)

        drawOrnamentRule(canvas, y = JournalPdfMetrics.MARGIN_TOP + 130f)
    }

    private fun drawOrnamentRule(
        canvas: Canvas,
        y: Float,
    ) {
        val rulePaint =
            Paint().apply {
                color = JournalPdfMetrics.COLOR_INK.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 0.6f
                isAntiAlias = true
            }
        val centerOrnamentPaint =
            caveatPaint(textSize = JournalPdfMetrics.ORNAMENT, color = JournalPdfMetrics.COLOR_COPPER.toInt(), center = true)
        val rulePadding = 30f
        val leftEnd = JournalPdfMetrics.PAGE_W / 2f - rulePadding
        val rightStart = JournalPdfMetrics.PAGE_W / 2f + rulePadding
        canvas.drawLine(JournalPdfMetrics.MARGIN_X + 40f, y, leftEnd, y, rulePaint)
        canvas.drawLine(rightStart, y, JournalPdfMetrics.PAGE_W - JournalPdfMetrics.MARGIN_X - 40f, y, rulePaint)
        canvas.drawText(JournalPdfMetrics.ORNAMENT_GLYPH, JournalPdfMetrics.PAGE_W / 2f, y + 5f, centerOrnamentPaint)
    }

    private fun drawPageFooter(
        canvas: Canvas,
        pageNum: Int,
    ) {
        val pageMarkPaint = caveatPaint(textSize = JournalPdfMetrics.FOOTER, color = JournalPdfMetrics.COLOR_INK.toInt(), center = true)
        val footerText = JournalPdfMetrics.fmt(JournalPdfMetrics.FOOTER_FMT, "$pageNum")
        canvas.drawText(
            footerText,
            JournalPdfMetrics.PAGE_W / 2f,
            JournalPdfMetrics.PAGE_H - JournalPdfMetrics.MARGIN_BOTTOM / 2f,
            pageMarkPaint,
        )
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
}
