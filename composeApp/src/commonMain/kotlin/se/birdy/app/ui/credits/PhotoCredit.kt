package se.birdy.app.ui.credits

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.components.META_TEXT_ALPHA
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.model.SpeciesImage

/**
 * A species photo's credit (release 1.3.0, legal review §2, Task 7e-2): the photographer, the
 * licence linked to its deed and the photo's file page on Wikimedia Commons, plus "nedskalad"
 * for a CC BY or CC BY-SA photo, since the app's copy is resized (the licences ask that a change
 * is noted; a CC BY-SA photo is shared under the same licence, which the linked deed states).
 * CC0 and public domain photos need neither, but are still credited ("Foto: X · CC0 · ...").
 *
 * [PhotoCreditForm.Full] links "Wikimedia Commons" to the file page. [PhotoCreditForm.Compact]
 * is the photographer and the licence only, "Foto: X" linked to the file page; the profile and
 * the "Bildkällor" list under About carry the rest. [linkStyles] null gives the same text without
 * links (a credit inside a button).
 */
internal fun photoCreditText(
    image: SpeciesImage,
    words: PhotoCreditWords,
    form: PhotoCreditForm,
    linkStyles: TextLinkStyles?,
): AnnotatedString =
    buildAnnotatedString {
        val pieces = mutableListOf<CreditPart>()
        if (image.author.isNotBlank()) {
            val byline = linkedTemplate(words.photoBy, listOf(CreditPart(image.author.trim())), linkStyles = null).text
            pieces += CreditPart(byline, url = image.filePageUrl.takeIf { form == PhotoCreditForm.Compact })
        }
        val licence = if (image.license == PUBLIC_DOMAIN) words.publicDomain else image.license
        pieces += CreditPart(licence.keepTogether(), image.licenseUrl)
        if (form == PhotoCreditForm.Full) pieces += CreditPart(COMMONS.keepTogether(), image.filePageUrl)
        if (form == PhotoCreditForm.Full && !image.isPublicDomain) pieces += CreditPart(words.resized)
        pieces.forEachIndexed { index, part ->
            if (index > 0) append(SEPARATOR)
            appendPart(part, linkStyles)
        }
    }

/** Separates the parts of a credit (a middle dot, never a dash). */
internal const val SEPARATOR = " · "
private const val COMMONS = "Wikimedia Commons"
private const val PUBLIC_DOMAIN = "Public domain"

internal val CreditFontSize = 11.sp
internal val CreditLineHeight = 15.sp

/** The credit text's color right under a photo, on [se.birdy.app.ui.theme.PhotoBand]. */
internal val CreditOnBand: Color = TextOnHero.copy(alpha = META_TEXT_ALPHA)

/** The credit text's color on paper. */
internal val CreditOnPaper: Color = InkMuted

/** Links in a credit: the text's own color, underlined, so they read as links without a new hue. */
internal fun creditLinkStyles(color: Color): TextLinkStyles =
    TextLinkStyles(style = SpanStyle(color = color, textDecoration = TextDecoration.Underline))

/**
 * A photo's credit as one text (one TalkBack stop that reads the whole credit; each link is its
 * own focusable link inside it). [onBand]: right under a photo on the dark band (Match, the
 * profile's photo top), flush right, else on paper. [withLinks] false inside a button (a Disambig
 * card).
 */
@Composable
internal fun PhotoCredit(
    image: SpeciesImage,
    form: PhotoCreditForm,
    modifier: Modifier = Modifier,
    onBand: Boolean = false,
    withLinks: Boolean = true,
) {
    val color = if (onBand) CreditOnBand else CreditOnPaper
    val words = rememberPhotoCreditWords()
    Text(
        text = photoCreditText(image, words, form, linkStyles = creditLinkStyles(color).takeIf { withLinks }),
        color = color,
        fontSize = CreditFontSize,
        lineHeight = CreditLineHeight,
        // Under a photo the credit sits flush right, like a caption's credit line.
        textAlign = if (onBand) TextAlign.End else TextAlign.Start,
        modifier = modifier,
    )
}
