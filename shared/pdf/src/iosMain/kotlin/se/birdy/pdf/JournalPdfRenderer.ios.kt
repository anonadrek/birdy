package se.birdy.pdf

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat

/**
 * iOS-actualen. Rittvillingen [JournalPdfLayoutIos] speglar [JournalPdfLayout] (androidMain)
 * sida för sida; sidnumreringen här speglar [JournalPdfRenderer.android.kt]s `pageNum`-bokföring
 * EXAKT: `pageNum` ökas efter VARJE ritad sida UTOM den sista (colophon) — `pageCount` sätts
 * till `pageNum`s då-aktuella värde direkt efter colophon, utan ytterligare ökning. Ett generiskt
 * "rita-och-öka-alltid"-hjälpblock skulle räkna fel (5 istället för 4 i no-badges-scenariot,
 * eftersom det skulle öka pageNum även efter colophon) — se iosTestet för kontraktet.
 */
actual class JournalPdfRenderer actual constructor() {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual suspend fun render(
        input: JournalPdfInput,
        outputPath: String,
    ): JournalPdfRenderResult =
        withContext(Dispatchers.Default) {
            if (input.observations.isEmpty()) return@withContext JournalPdfRenderResult.Empty
            runCatching {
                val zone = TimeZone.currentSystemDefault()
                val bounds = CGRectMake(0.0, 0.0, JournalPdfMetrics.PAGE_W.toDouble(), JournalPdfMetrics.PAGE_H.toDouble())
                val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = UIGraphicsPDFRendererFormat.defaultFormat())
                var pageCount = 0
                val url = NSURL.fileURLWithPath(outputPath)
                // säkra parent-katalogen (kontraktet: caller ger app-privat path)
                NSFileManager.defaultManager.createDirectoryAtPath(
                    outputPath.substringBeforeLast('/'),
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )
                val ok =
                    memScoped {
                        val err = alloc<ObjCObjectVar<NSError?>>()
                        val success =
                            renderer.writePDFToURL(
                                url,
                                withActions = { ctxOrNull ->
                                    // K/N binds the block parameter as nullable (UIGraphicsPDFRendererContext?)
                                    // despite the header's NS_HEADER_AUDIT_BEGIN(nullability,…) region — verified
                                    // by compiling, not assumed. Materialize a non-null local once instead of
                                    // repeating `!!` at every beginPage() call.
                                    val ctx = ctxOrNull!!
                                    var pageNum = 1

                                    ctx.beginPage()
                                    JournalPdfLayoutIos.drawTitlePage(input, pageNum, zone)
                                    pageNum++

                                    ctx.beginPage()
                                    JournalPdfLayoutIos.drawStatsPage(input, pageNum, zone)
                                    pageNum++

                                    val speciesPages = JournalPageAggregator.computeSpeciesPages(input)
                                    speciesPages.forEachIndexed { idx, rows ->
                                        ctx.beginPage()
                                        JournalPdfLayoutIos.drawSpeciesPage(input, rows, pageNum, idx, speciesPages.size, zone)
                                        pageNum++
                                    }

                                    if (input.unlockedPremiumBadges.isNotEmpty()) {
                                        ctx.beginPage()
                                        JournalPdfLayoutIos.drawBadgesPage(input, pageNum, zone)
                                        pageNum++
                                    }

                                    ctx.beginPage()
                                    JournalPdfLayoutIos.drawColophonPage(input, pageNum, zone)
                                    // Ingen pageNum++ här — spegel av Android: totalPages == colophon-sidans
                                    // eget nummer, inte "nästa" nummer. Se klass-KDoc:en ovan.
                                    pageCount = pageNum
                                },
                                error = err.ptr,
                            )
                        if (!success) throw IllegalStateException("writePDFToURL failed: ${err.value?.localizedDescription}")
                        success
                    }
                check(ok)
                val size =
                    (NSFileManager.defaultManager.attributesOfItemAtPath(outputPath, error = null)?.get(NSFileSize) as? NSNumber)
                        ?.longLongValue
                        ?: 0L
                JournalPdfRenderResult.Success(pageCount = pageCount, sizeBytes = size)
            }.getOrElse { t -> JournalPdfRenderResult.Failed("PDF render failed: ${t.message}", t) }
        }
}
