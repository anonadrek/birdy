package se.birdy.pdf

import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual class JournalPdfRenderer actual constructor() {
    actual suspend fun render(
        input: JournalPdfInput,
        outputPath: String,
    ): JournalPdfRenderResult =
        withContext(Dispatchers.IO) {
            if (input.observations.isEmpty()) return@withContext JournalPdfRenderResult.Empty

            val doc = PdfDocument()
            try {
                var pageNum = 1
                JournalPdfLayout.drawTitlePage(doc, input, pageNum)
                pageNum++
                JournalPdfLayout.drawStatsPage(doc, input, pageNum)
                pageNum++

                val speciesPages = JournalPageAggregator.computeSpeciesPages(input)
                speciesPages.forEachIndexed { idx, rows ->
                    JournalPdfLayout.drawSpeciesPage(
                        doc = doc,
                        input = input,
                        rows = rows,
                        pageNum = pageNum,
                        pageIndex = idx,
                        totalSpeciesPages = speciesPages.size,
                    )
                    pageNum++
                }

                if (input.unlockedPremiumBadges.isNotEmpty()) {
                    JournalPdfLayout.drawBadgesPage(doc, input, pageNum)
                    pageNum++
                }

                JournalPdfLayout.drawColophonPage(doc, input, pageNum)
                val totalPages = pageNum

                val outFile = File(outputPath)
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { doc.writeTo(it) }

                JournalPdfRenderResult.Success(
                    pageCount = totalPages,
                    sizeBytes = outFile.length(),
                )
            } catch (t: Throwable) {
                JournalPdfRenderResult.Failed("PDF render failed: ${t.message}", t)
            } finally {
                doc.close()
            }
        }
}
