package se.birdy.app.ui.settings.credits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/*
 * Shared by About and the credit pages under it (release 1.3.0: photo credits, Task 7e-2; open-source
 * licences, legal review 7i-fix B).
 */

/** A phrase in a localized text that links to [url]. The phrase is a name (a licence, a project). */
internal data class TextLink(
    val phrase: String,
    val url: String,
)

/** Underlined rust, the link style on paper. */
internal val CreditLinkStyles =
    TextLinkStyles(style = SpanStyle(color = AccentCopper, textDecoration = TextDecoration.Underline))

/**
 * [text] with the first occurrence of every [links] phrase made a link. A phrase the text does not
 * contain stays plain text; `AboutCreditsTest` checks that every phrase is in both languages.
 */
internal fun linkify(
    text: String,
    links: List<TextLink>,
    styles: TextLinkStyles = CreditLinkStyles,
): AnnotatedString {
    val ranges =
        links
            .mapNotNull { link -> text.indexOf(link.phrase).takeIf { it >= 0 }?.let { it to link } }
            .sortedBy { it.first }
            // A phrase inside an earlier link ("CC0" inside "CC0 1.0") is skipped, never nested.
            .fold(mutableListOf<Pair<Int, TextLink>>()) { kept, next ->
                val last = kept.lastOrNull()
                if (last == null || next.first >= last.first + last.second.phrase.length) kept += next
                kept
            }
    return buildAnnotatedString {
        var cursor = 0
        for ((start, link) in ranges) {
            append(text.substring(cursor, start))
            withLink(LinkAnnotation.Url(link.url, styles)) { append(link.phrase) }
            cursor = start + link.phrase.length
        }
        append(text.substring(cursor))
    }
}

private val URL_IN_TEXT = Regex("""https?://[^\s<>"]+""")

/**
 * [text] with every web address in it made a link (a licence or notice text that names its source),
 * without the punctuation that ends a sentence or closes a parenthesis after it.
 */
internal fun linkifyUrls(
    text: String,
    styles: TextLinkStyles = CreditLinkStyles,
): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        for (match in URL_IN_TEXT.findAll(text)) {
            val url = match.value.trimEnd('.', ',', ';', ':', ')', ']', '\'', '>')
            val start = match.range.first
            append(text.substring(cursor, start))
            withLink(LinkAnnotation.Url(url, styles)) { append(url) }
            cursor = start + url.length
        }
        append(text.substring(cursor))
    }

/**
 * Links in [content] open through [onOpenUrl] (the browser, via `openExternalUrl`, in the app; a
 * recorder in tests) instead of Compose's own default handler.
 */
@Composable
internal fun ProvideUrlOpener(
    onOpenUrl: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val handler =
        remember(onOpenUrl) {
            object : UriHandler {
                override fun openUri(uri: String) = onOpenUrl(uri)
            }
        }
    CompositionLocalProvider(LocalUriHandler provides handler, content = content)
}

/** The fixed top bar of a pushed credits page: the back button and, when given, the page title. */
@Composable
internal fun CreditsTopBar(
    onBack: () -> Unit,
    backDescription: String,
    title: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBack, contentDescription = backDescription)
        Spacer(Modifier.size(8.dp))
        if (title != null) {
            Text(
                text = title,
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 22.sp,
                color = TextOnCreme,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
    }
}

/** A credits page's content while it loads, once it has, or when it could not be read. */
internal sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    data class Loaded<T>(
        val value: T,
    ) : Loadable<T>

    data object Failed : Loadable<Nothing>
}

// TooGenericExceptionCaught: any failure (a missing file, bad JSON, a database error) ends in the
// error state, logged.

/**
 * Runs [load] once per [key] and holds its result. A failure gives [Loadable.Failed] and a log line;
 * the page then says it could not be read instead of spinning forever.
 */
@Suppress("TooGenericExceptionCaught")
@Composable
internal fun <T> rememberLoadable(
    key: Any?,
    load: suspend () -> T,
): State<Loadable<T>> =
    produceState<Loadable<T>>(initialValue = Loadable.Loading, key) {
        value =
            try {
                Loadable.Loaded(load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Birdy/credits: loading failed: $e")
                Loadable.Failed
            }
    }

/** The centred message a credits page shows when its content could not be read. */
@Composable
internal fun CreditsError(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = message, color = MarginaliaInk, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}
