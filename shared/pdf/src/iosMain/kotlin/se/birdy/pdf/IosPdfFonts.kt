package se.birdy.pdf

import platform.UIKit.UIFont

/**
 * PDF-typsnitten på iOS. Filerna bundlas via project.yml + laddas av UIAppFonts
 * (Info.plist). PostScript-namnen matchar TTF-metadatan. I test-binärer (egen process
 * utan app-Info.plist) är namnen oregistrerade → systemfallback, per spec §E:
 * typsnittsfel får aldrig faila en render.
 */
internal object IosPdfFonts {
    fun dmSerifItalic(size: Double): UIFont = UIFont.fontWithName("DMSerifDisplay-Italic", size) ?: UIFont.italicSystemFontOfSize(size)

    fun caveat(size: Double): UIFont = UIFont.fontWithName("Caveat-Regular", size) ?: UIFont.systemFontOfSize(size)
}
