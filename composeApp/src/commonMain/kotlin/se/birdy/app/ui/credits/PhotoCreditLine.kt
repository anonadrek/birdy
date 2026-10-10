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
import se.birdy.content.PhotoLicenses
import se.birdy.content.model.PhotoCredit
import se.birdy.content.model.SpeciesImage

/**
 * A species photo's credit (release 1.3.0, legal review §2, Task 7e-2), the one format for the
 * species profile, Match, Disambig and the "Bildkällor" list under About: the photographer, the
 * licence linked to its deed, the photo's file page on Wikimedia Commons and "nedskalad", since the
 * app's copy of every photo is resized (the CC licences ask that a change is noted; a CC BY-SA photo
 * is shared under the same licence, which the linked deed states). "Public domain" reads in the
 * reader's language and has no deed to link.
 *
 * [PhotoCreditForm.Full] is all of it, "Wikimedia Commons" linked to the file page.
 * [PhotoCreditForm.LicenseLine] leaves out "Foto: X" (Bildkällor shows it on its own line).
 * [PhotoCreditForm.Compact] is the photographer and the licence only, "Foto: X" linked to the file
 * page. [linkStyles] null gives the same text without links (a credit inside a button).
 */
internal fun photoCreditText(
    photo: CreditedPhoto,
    words: PhotoCreditWords,
    form: PhotoCreditForm,
    linkStyles: TextLinkStyles?,
): AnnotatedString =
    buildAnnotatedString {
        val pieces = mutableListOf<CreditPart>()
        if (photo.author.isNotBlank() && form != PhotoCreditForm.LicenseLine) {
            val byline = linkedTemplate(words.photoBy, listOf(CreditPart(photo.author.trim())), linkStyles = null).text
            pieces += CreditPart(byline, url = photo.filePageUrl.takeIf { form == PhotoCreditForm.Compact })
        }
        pieces += CreditPart(licenseLabel(photo.license, words).keepTogether(), photo.licenseUrl)
        if (form != PhotoCreditForm.Compact) {
            pieces += CreditPart(COMMONS.keepTogether(), photo.filePageUrl)
            pieces += CreditPart(words.resized)
        }
        pieces.forEachIndexed { index, part ->
            if (index > 0) append(SEPARATOR)
            appendPart(part, linkStyles)
        }
    }

/** What a photo's credit names: the same for a photo on a species page and in "Bildkällor". */
internal data class CreditedPhoto(
    val author: String,
    val license: String,
    val licenseUrl: String?,
    val filePageUrl: String,
)

internal fun SpeciesImage.credited(): CreditedPhoto = CreditedPhoto(author, license, licenseUrl, filePageUrl)

internal fun PhotoCredit.credited(): CreditedPhoto = CreditedPhoto(author, license, licenseUrl, filePageUrl)

/** [photoCreditText] for a photo on a species page. */
internal fun photoCreditText(
    image: SpeciesImage,
    words: PhotoCreditWords,
    form: PhotoCreditForm,
    linkStyles: TextLinkStyles?,
): AnnotatedString = photoCreditText(image.credited(), words, form, linkStyles)

/** [photoCreditText] for a photo in the "Bildkällor" list. */
internal fun photoCreditText(
    credit: PhotoCredit,
    words: PhotoCreditWords,
    form: PhotoCreditForm,
    linkStyles: TextLinkStyles?,
): AnnotatedString = photoCreditText(credit.credited(), words, form, linkStyles)

/** A photo's licence as the reader sees it: its name, or "public domain" in their language. */
internal fun licenseLabel(
    license: String,
    words: PhotoCreditWords,
): String = if (license == PhotoLicenses.PUBLIC_DOMAIN) words.publicDomain else license

/**
 * Separates the parts of a credit: a middle dot, never a dash, kept on the line of the part before
 * it (a no-break space), so a line never starts with "· nedskalad".
 */
internal const val SEPARATOR = "$NO_BREAK_SPACE· "
private const val COMMONS = "Wikimedia Commons"

internal val CreditFontSize = 11.sp
internal val CreditLineHeight = 15.sp

/** The credit text's color right under a photo, on [se.birdy.app.ui.theme.PhotoBand]. */
internal val CreditOnBand: Color = TextOnHero.copy(alpha = META_TEXT_ALPHA)

/** The credit text's color on paper. */
internal val CreditOnPaper: Color = InkMuted

/**
 * Links in the credits and on the About pages: underlined in the text's own color, so they read as
 * links without a new hue and keep the text's contrast, also on the dark band under a photo (rust
 * would fall to 2.6:1 there).
 */
internal val CreditLinkStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))

/**
 * A photo's credit as one text (one TalkBack stop that reads the whole credit; each link is its
 * own focusable link inside it). [onBand]: right under a photo on the dark band (Match, the
 * profile's photo top), flush right, else on paper. [withLinks] false inside a button (a Disambig
 * card). The links open through the app's url opener (AppScaffold's ProvideUrlOpener).
 */
@Composable
internal fun PhotoCreditLine(
    image: SpeciesImage,
    form: PhotoCreditForm,
    modifier: Modifier = Modifier,
    onBand: Boolean = false,
    withLinks: Boolean = true,
) {
    val color = if (onBand) CreditOnBand else CreditOnPaper
    Text(
        text = photoCreditText(image, photoCreditWords(), form, linkStyles = CreditLinkStyles.takeIf { withLinks }),
        color = color,
        fontSize = CreditFontSize,
        lineHeight = CreditLineHeight,
        // Under a photo the credit sits flush right, like a caption's credit line.
        textAlign = if (onBand) TextAlign.End else TextAlign.Start,
        modifier = modifier,
    )
}
