package se.birdy.app.ui.credits

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_by
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_public_domain
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_resized
import birdy_bird_scanner.composeapp.generated.resources.profile_text_credit
import birdy_bird_scanner.composeapp.generated.resources.profile_text_credit_article
import birdy_bird_scanner.composeapp.generated.resources.profile_text_credit_article_en
import birdy_bird_scanner.composeapp.generated.resources.profile_text_credit_article_sv
import birdy_bird_scanner.composeapp.generated.resources.profile_text_credit_language_en
import birdy_bird_scanner.composeapp.generated.resources.profile_text_credit_language_sv
import birdy_bird_scanner.composeapp.generated.resources.profile_text_credit_two
import org.jetbrains.compose.resources.stringResource
import se.birdy.content.Locale

// The words the credits are built from (release 1.3.0, Task 7e-2 and 7i-fix A): the string
// resources of the app's language, read for each credit, and the pieces a credit is put together
// from. Plain values, so the credit texts can be built and tested without a composition.

/** A piece of a credit: [text], linked to [url] when there is one. */
internal data class CreditPart(
    val text: String,
    val url: String? = null,
)

/**
 * The words of a photo credit in the app's language. The same strings as the "Bildkällor" list
 * under About, so a photo is credited alike on its species page and there.
 */
internal data class PhotoCreditWords(
    // "Foto: %1$s"
    val photoBy: String,
    // "nedskalad": the app's copy of every photo is resized (CC0 and public domain too).
    val resized: String,
    // What a "Public domain" photo's licence reads as.
    val publicDomain: String,
)

@Composable
internal fun photoCreditWords(): PhotoCreditWords =
    PhotoCreditWords(
        photoBy = stringResource(Res.string.photo_credits_by),
        resized = stringResource(Res.string.photo_credits_resized),
        publicDomain = stringResource(Res.string.photo_credits_public_domain),
    )

/** How much of a photo's credit a place shows. */
internal enum class PhotoCreditForm {
    /** The species profile: "Foto: X · CC BY 4.0 · Wikimedia Commons · nedskalad". */
    Full,

    /**
     * The "Bildkällor" list, which shows "Foto: X" on a line of its own above: "CC BY 4.0 ·
     * Wikimedia Commons · nedskalad".
     */
    LicenseLine,

    /**
     * Match and Disambig, where the photo is not the subject and the room is short: "Foto: X ·
     * CC BY 4.0", one line in most cases. The species profile and "Bildkällor" have the full credit.
     */
    Compact,
}

/** The words of the species text's credit in the app's language, from the string resources. */
internal data class TextCreditWords(
    // "Texten är en AI-sammanfattning av %1$s och får delas under %2$s."
    val oneArticle: String,
    // "Texten är en AI-sammanfattning av Wikipedia-artiklarna på %1$s och %2$s och får delas under %3$s."
    val twoArticles: String,
    // "Wikipedia-artikeln": the article in the app's own language.
    val article: String,
    // "den engelska Wikipedia-artikeln": the article in another language (the English fallback).
    val articleIn: Map<Locale, String>,
    // "svenska", "engelska", for two articles.
    val languageName: Map<Locale, String>,
)

@Composable
internal fun textCreditWords(): TextCreditWords =
    TextCreditWords(
        oneArticle = stringResource(Res.string.profile_text_credit),
        twoArticles = stringResource(Res.string.profile_text_credit_two),
        article = stringResource(Res.string.profile_text_credit_article),
        articleIn =
            mapOf(
                Locale.SV to stringResource(Res.string.profile_text_credit_article_sv),
                Locale.EN to stringResource(Res.string.profile_text_credit_article_en),
            ),
        languageName =
            mapOf(
                Locale.SV to stringResource(Res.string.profile_text_credit_language_sv),
                Locale.EN to stringResource(Res.string.profile_text_credit_language_en),
            ),
    )
