package se.birdy.pdf

import android.content.Context
import android.graphics.Typeface

/**
 * Loads PDF-rendering typefaces from the app's `assets/fonts/` directory once and exposes them
 * for [JournalPdfRenderer].
 *
 * Must be initialized with an Android [Context] on first use. The renderer is callable from
 * background coroutines; once [init] has been called from the main process the typefaces
 * are safe to read from any thread.
 */
object PdfFontProvider {
    @Volatile private var dmSerifItalic: Typeface? = null

    @Volatile private var caveat: Typeface? = null

    fun init(context: Context) {
        if (dmSerifItalic == null) {
            dmSerifItalic = Typeface.createFromAsset(context.assets, "fonts/DMSerifDisplay-Italic.ttf")
        }
        if (caveat == null) {
            caveat = Typeface.createFromAsset(context.assets, "fonts/Caveat-Regular.ttf")
        }
    }

    fun dmSerifItalic(): Typeface = dmSerifItalic ?: error("PdfFontProvider.init not called")

    fun caveat(): Typeface = caveat ?: error("PdfFontProvider.init not called")
}
