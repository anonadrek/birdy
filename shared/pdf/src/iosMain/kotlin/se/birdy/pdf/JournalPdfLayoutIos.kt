package se.birdy.pdf

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.datetime.TimeZone
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.drawAtPoint
import platform.UIKit.sizeWithAttributes

/**
 * iOS-tvillingen till JournalPdfLayout (androidMain). Samma fem sidor, samma
 * JournalPdfMetrics-koordinater — varje tal nedan är kopierat rad för rad ur
 * JournalPdfLayout.kt, inget värde uppfinns här. Skillnad mot Android: Canvas.drawText
 * tar BASELINE-y, NSString.drawAtPoint tar TOPP-y → [drawText] översätter
 * (top = baseline - ascender) så siffervärdena kan kopieras rakt av från Android-filen.
 * `PdfDocument.Canvas`s `Paint.Align.CENTER`/vänsterjustering speglas manuellt: `centered`
 * flyttar origin ett halvt strängbredd åt vänster (motsvarar Androids Align.CENTER), annars
 * ritas strängen med vänsterkant vid `x` (motsvarar Androids default Align.LEFT).
 */
@OptIn(ExperimentalForeignApi::class)
internal object JournalPdfLayoutIos {
    private val M = JournalPdfMetrics

    private fun uiColor(argb: Long): UIColor =
        UIColor(
            red = ((argb shr 16) and 0xFF).toDouble() / 255.0,
            green = ((argb shr 8) and 0xFF).toDouble() / 255.0,
            blue = (argb and 0xFF).toDouble() / 255.0,
            alpha = ((argb shr 24) and 0xFF).toDouble() / 255.0,
        )

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun drawText(
        text: String,
        x: Double,
        baselineY: Double,
        font: UIFont,
        color: Long,
        centered: Boolean = false,
    ) {
        val ns = text as NSString
        val attrs: Map<Any?, *> = mapOf(NSFontAttributeName to font, NSForegroundColorAttributeName to uiColor(color))
        val width = ns.sizeWithAttributes(attrs).useContents { width }
        val originX = if (centered) x - width / 2.0 else x
        ns.drawAtPoint(CGPointMake(originX, baselineY - font.ascender), withAttributes = attrs)
    }

    private fun fillRect(
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        color: Long,
    ) {
        uiColor(color).setFill()
        UIBezierPath.bezierPathWithRect(CGRectMake(x, y, w, h)).fill()
    }

    private fun strokeLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: Long,
        width: Double,
    ) {
        uiColor(color).setStroke()
        UIBezierPath()
            .apply {
                moveToPoint(CGPointMake(x1, y1))
                addLineToPoint(CGPointMake(x2, y2))
                lineWidth = width
            }.stroke()
    }

    private fun strokeCircle(
        cx: Double,
        cy: Double,
        r: Double,
        color: Long,
        width: Double,
    ) {
        uiColor(color).setStroke()
        UIBezierPath
            .bezierPathWithOvalInRect(CGRectMake(cx - r, cy - r, r * 2.0, r * 2.0))
            .apply { lineWidth = width }
            .stroke()
    }

    private fun paintPaperBg() = fillRect(0.0, 0.0, M.PAGE_W.toDouble(), M.PAGE_H.toDouble(), M.COLOR_PAPER_BG)

    private fun drawOrnamentRule(y: Double) {
        val rulePadding = 30.0
        strokeLine(M.MARGIN_X + 40.0, y, M.PAGE_W / 2.0 - rulePadding, y, M.COLOR_INK, 0.6)
        strokeLine(M.PAGE_W / 2.0 + rulePadding, y, M.PAGE_W - M.MARGIN_X - 40.0, y, M.COLOR_INK, 0.6)
        drawText(M.ORNAMENT_GLYPH, M.PAGE_W / 2.0, y + 5.0, IosPdfFonts.caveat(M.ORNAMENT.toDouble()), M.COLOR_COPPER, centered = true)
    }

    private fun drawSectionHeader(
        eyebrow: String,
        title: String,
    ) {
        drawText(
            eyebrow,
            M.PAGE_W / 2.0,
            M.MARGIN_TOP + 50.0,
            IosPdfFonts.caveat(M.SECTION_EYEBROW.toDouble()),
            M.COLOR_COPPER,
            centered = true,
        )
        drawText(
            title,
            M.PAGE_W / 2.0,
            M.MARGIN_TOP + 100.0,
            IosPdfFonts.dmSerifItalic(M.SECTION_TITLE.toDouble()),
            M.COLOR_INK,
            centered = true,
        )
        drawOrnamentRule(M.MARGIN_TOP + 130.0)
    }

    private fun drawPageFooter(pageNum: Int) =
        drawText(
            M.fmt(M.FOOTER_FMT, pageNum.toString()),
            M.PAGE_W / 2.0,
            M.PAGE_H - M.MARGIN_BOTTOM / 2.0,
            IosPdfFonts.caveat(M.FOOTER.toDouble()),
            M.COLOR_INK,
            centered = true,
        )

    fun drawTitlePage(
        input: JournalPdfInput,
        pageNum: Int,
        zone: TimeZone,
    ) {
        paintPaperBg()
        val year = M.yearOf(input.generatedAtMs, zone)
        drawText(
            M.ORNAMENT_GLYPH,
            M.PAGE_W / 2.0,
            M.MARGIN_TOP + 30.0,
            IosPdfFonts.caveat(M.ORNAMENT_TOP.toDouble()),
            M.COLOR_COPPER,
            centered = true,
        )
        drawText(
            M.TITLE,
            M.PAGE_W / 2.0,
            M.MARGIN_TOP + 150.0,
            IosPdfFonts.dmSerifItalic(M.TITLE_SIZE.toDouble()),
            M.COLOR_INK,
            centered = true,
        )
        drawText(
            M.fmt(M.BY_FMT, input.displayName),
            M.PAGE_W / 2.0,
            M.MARGIN_TOP + 188.0,
            IosPdfFonts.caveat(M.TITLE_SUB.toDouble()),
            M.COLOR_INK,
            centered = true,
        )
        drawText(
            "$year",
            M.PAGE_W / 2.0,
            M.MARGIN_TOP + 240.0,
            IosPdfFonts.dmSerifItalic(M.TITLE_YEAR.toDouble()),
            M.COLOR_COPPER,
            centered = true,
        )
        drawOrnamentRule(M.MARGIN_TOP + 280.0)
        drawText(
            M.fmt(
                M.TEASER_FMT,
                input.stats.speciesSeenThisYear.toString(),
                input.stats.totalObservationsThisYear.toString(),
            ),
            M.PAGE_W / 2.0,
            M.MARGIN_TOP + 322.0,
            IosPdfFonts.caveat(M.TITLE_TEASER.toDouble()),
            M.COLOR_INK,
            centered = true,
        )
        drawPageFooter(pageNum)
    }

    fun drawStatsPage(
        input: JournalPdfInput,
        pageNum: Int,
        zone: TimeZone,
    ) {
        paintPaperBg()
        val year = M.yearOf(input.generatedAtMs, zone)
        drawSectionHeader(eyebrow = M.STATS_EYEBROW, title = M.fmt(M.STATS_TITLE_FMT, "$year"))

        val colW = (M.PAGE_W - 2 * M.MARGIN_X) / 2.0
        val statsY = M.MARGIN_TOP + 200.0
        drawText(
            "${input.stats.speciesSeenThisYear}",
            M.MARGIN_X + 10.0,
            statsY,
            IosPdfFonts.dmSerifItalic(M.STAT_NUMBER.toDouble()),
            M.COLOR_COPPER,
        )
        drawText(M.STAT_SPECIES, M.MARGIN_X + 10.0, statsY + 22.0, IosPdfFonts.caveat(M.STAT_CAPTION.toDouble()), M.COLOR_INK)
        drawText(
            "${input.stats.totalObservationsThisYear}",
            M.MARGIN_X + colW + 10.0,
            statsY,
            IosPdfFonts.dmSerifItalic(M.STAT_NUMBER.toDouble()),
            M.COLOR_COPPER,
        )
        drawText(M.STAT_TOTAL, M.MARGIN_X + colW + 10.0, statsY + 22.0, IosPdfFonts.caveat(M.STAT_CAPTION.toDouble()), M.COLOR_INK)

        // Top species bar chart (max 5)
        if (input.stats.topSpecies.isNotEmpty()) {
            val chartTop = statsY + 80.0
            drawText(M.TOPS, M.MARGIN_X.toDouble(), chartTop, IosPdfFonts.dmSerifItalic(M.TOPS_HEADER.toDouble()), M.COLOR_INK)

            val barAreaX = M.MARGIN_X + 140.0
            val barAreaW = M.PAGE_W - barAreaX - M.MARGIN_X
            val rowH = 28.0
            val maxCount = (input.stats.topSpecies.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

            input.stats.topSpecies.take(5).forEachIndexed { i, (name, count) ->
                val y = chartTop + 24.0 + i * rowH
                drawText(name, M.MARGIN_X.toDouble(), y + 14.0, IosPdfFonts.caveat(M.BAR_LABEL.toDouble()), M.COLOR_INK)
                val barY = y + 6.0
                val barH = 14.0
                fillRect(barAreaX, barY, barAreaW, barH, M.COLOR_PAPER_EDGE)
                val filled = barAreaW * (count.toDouble() / maxCount)
                fillRect(barAreaX, barY, filled, barH, M.COLOR_COPPER)
                drawText("$count", barAreaX + filled + 6.0, barY + 12.0, IosPdfFonts.caveat(M.BAR_VALUE.toDouble()), M.COLOR_COPPER)
            }
        }

        drawPageFooter(pageNum)
    }

    fun drawSpeciesPage(
        input: JournalPdfInput,
        rows: List<JournalPageAggregator.SpeciesRow>,
        pageNum: Int,
        pageIndex: Int,
        totalSpeciesPages: Int,
        zone: TimeZone,
    ) {
        paintPaperBg()
        val eyebrow =
            if (totalSpeciesPages > 1) {
                M.fmt(M.SPECIES_EYEBROW_PAGED_FMT, "${pageIndex + 1}", "$totalSpeciesPages")
            } else {
                M.SPECIES_EYEBROW
            }
        drawSectionHeader(eyebrow = eyebrow, title = M.SPECIES_TITLE)

        val rowTop = M.MARGIN_TOP + 170.0
        val rowH = 24.0

        rows.forEachIndexed { i, row ->
            val y = rowTop + i * rowH

            // Thumbnail-placeholder rect: Android RectF(MARGIN_X, y-12f, MARGIN_X+18f, y+6f) → 18×18.
            fillRect(M.MARGIN_X.toDouble(), y - 12.0, 18.0, 18.0, M.COLOR_PAPER_EDGE)

            drawText(row.nameLocalized, M.MARGIN_X + 26.0, y, IosPdfFonts.dmSerifItalic(M.SPECIES_NAME.toDouble()), M.COLOR_INK)
            if (row.scientificName.isNotEmpty()) {
                drawText(row.scientificName, M.MARGIN_X + 26.0, y + 12.0, IosPdfFonts.caveat(M.SPECIES_SCI.toDouble()), M.COLOR_INK)
            }

            val countText = M.fmt(M.COUNT_FMT, "${row.count}")
            val firstSeenDate = M.formatDate(row.firstSeenMs, zone)
            val firstSeenText = M.fmt(M.FIRST_FMT, firstSeenDate)
            val rightX = M.PAGE_W - M.MARGIN_X - 120.0
            drawText(countText, rightX, y, IosPdfFonts.dmSerifItalic(M.SPECIES_COUNT.toDouble()), M.COLOR_COPPER)
            drawText(firstSeenText, rightX, y + 12.0, IosPdfFonts.caveat(M.SPECIES_DATE.toDouble()), M.COLOR_INK)

            // Hairline rule
            strokeLine(M.MARGIN_X.toDouble(), y + 14.0, M.PAGE_W - M.MARGIN_X.toDouble(), y + 14.0, M.COLOR_PAPER_EDGE, 0.5)
        }

        drawPageFooter(pageNum)
    }

    fun drawBadgesPage(
        input: JournalPdfInput,
        pageNum: Int,
        zone: TimeZone,
    ) {
        paintPaperBg()
        drawSectionHeader(eyebrow = M.BADGES_EYEBROW, title = M.BADGES_TITLE)

        val rowTop = M.MARGIN_TOP + 180.0
        val rowH = 48.0

        input.unlockedPremiumBadges.take(10).forEachIndexed { i, badge ->
            val y = rowTop + i * rowH
            // Stamp circle
            strokeCircle(M.MARGIN_X + 16.0, y + 6.0, 14.0, M.COLOR_NAVY, 1.5)
            drawText(badge.nameLocalized, M.MARGIN_X + 44.0, y, IosPdfFonts.dmSerifItalic(M.BADGE_NAME.toDouble()), M.COLOR_INK)
            drawText(badge.descriptionLocalized, M.MARGIN_X + 44.0, y + 16.0, IosPdfFonts.caveat(M.BADGE_DESC.toDouble()), M.COLOR_INK)
            drawText(
                M.formatDate(badge.unlockedAt.toEpochMilliseconds(), zone),
                M.PAGE_W - M.MARGIN_X - 90.0,
                y + 4.0,
                IosPdfFonts.caveat(M.BADGE_DATE.toDouble()),
                M.COLOR_COPPER,
            )
        }

        drawPageFooter(pageNum)
    }

    fun drawColophonPage(
        input: JournalPdfInput,
        pageNum: Int,
        zone: TimeZone,
    ) {
        paintPaperBg()
        drawOrnamentRule(M.PAGE_H / 2.0 - 50.0)

        drawText(
            M.COLOPHON,
            M.PAGE_W / 2.0,
            M.PAGE_H / 2.0,
            IosPdfFonts.dmSerifItalic(M.COLOPHON_MARK.toDouble()),
            M.COLOR_INK,
            centered = true,
        )

        val generatedAt = M.formatDateTime(input.generatedAtMs, zone)
        drawText(
            M.fmt(M.GENERATED_FMT, generatedAt),
            M.PAGE_W / 2.0,
            M.PAGE_H / 2.0 + 28.0,
            IosPdfFonts.caveat(M.COLOPHON_GEN.toDouble()),
            M.COLOR_INK,
            centered = true,
        )

        drawOrnamentRule(M.PAGE_H / 2.0 + 70.0)

        drawPageFooter(pageNum)
    }
}
