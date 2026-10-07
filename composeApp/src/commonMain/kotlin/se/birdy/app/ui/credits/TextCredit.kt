package se.birdy.app.ui.credits

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import se.birdy.content.Locale
import se.birdy.content.WikipediaLinks
import se.birdy.content.model.SpeciesTextSource

/**
 * The species text's credit (release 1.3.0, legal review §4, 7i-fix A): the texts are AI summaries
 * of the species' Wikipedia article, so the credit links the article version behind the text
 * shown (or, where none is stored, the species' current article through Wikidata; the wording
 * says "Wikipedia-artikeln", never a version, so it is true for both), says the text is changed
 * and that it may be shared under CC BY-SA 4.0 (linked to the deed, in Swedish for the Swedish
 * app). [sources] come from the repository, one per language
 * among the texts shown: a Swedish user who sees the English fallback is pointed at "den engelska
 * Wikipedia-artikeln", and a profile with texts in both languages links both. Null when no text
 * is shown (no sources), so nothing is credited for the app's own empty-state lines.
 */
internal fun textCreditText(
    sources: List<SpeciesTextSource>,
    appLanguage: Locale,
    words: TextCreditWords,
    linkStyles: TextLinkStyles?,
): AnnotatedString? {
    val licence = CreditPart(WikipediaLinks.TEXT_LICENSE.keepTogether(), WikipediaLinks.textLicenseUrl(appLanguage))
    return when (sources.size) {
        0 -> null
        1 -> {
            val source = sources.single()
            val label = if (source.language == appLanguage) words.article else words.articleIn.getValue(source.language)
            linkedTemplate(words.oneArticle, listOf(CreditPart(label, source.articleUrl), licence), linkStyles)
        }
        else ->
            linkedTemplate(
                words.twoArticles,
                sources.take(2).map { CreditPart(words.languageName.getValue(it.language), it.articleUrl) } + licence,
                linkStyles,
            )
    }
}

/** [textCreditText] as one text on paper, or nothing when no species text is shown. */
@Composable
internal fun TextCredit(
    sources: List<SpeciesTextSource>,
    appLanguage: Locale,
    modifier: Modifier = Modifier,
) {
    val text =
        textCreditText(sources, appLanguage, rememberTextCreditWords(), creditLinkStyles(CreditOnPaper))
            ?: return
    Text(
        text = text,
        color = CreditOnPaper,
        fontSize = CreditFontSize,
        lineHeight = CreditLineHeight,
        modifier = modifier,
    )
}
