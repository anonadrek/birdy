package se.birdy.app.ui.settings.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.license_text_includes
import birdy_bird_scanner.composeapp.generated.resources.license_text_website
import birdy_bird_scanner.composeapp.generated.resources.licenses_error
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackTopBar
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.settings.openExternalUrl
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/** One entry's licence or notice text, read from the bundled files. */
@Composable
fun LicenseTextRoute(
    entryId: String,
    onBack: () -> Unit,
) {
    val text by rememberLoadable(entryId) { loadLicenseText(entryId) }
    LicenseTextScreen(state = text, onBack = onBack)
}

/**
 * The full licence or notice text of one entry of the open-source list (release 1.3.0, legal review
 * 7i-fix B): its name, version and licence, what it covers, its copyright lines and notice, then the
 * text, laid out one paragraph at a time.
 */
@Composable
internal fun LicenseTextScreen(
    state: Loadable<LicenseText>,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit = ::openExternalUrl,
) {
    ProvideUrlOpener(onOpenUrl) {
        JournalScaffold(
            topBar = { BackTopBar(onBack = onBack) },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (state) {
                    Loadable.Loading -> JournalLoading()
                    Loadable.Failed -> CreditsError(stringResource(Res.string.licenses_error))
                    is Loadable.Loaded -> LicenseTextBody(state.value)
                }
            }
        }
    }
}

@Composable
private fun LicenseTextBody(text: LicenseText) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp),
    ) {
        item(key = "header") { LicenseTextHeader(text.entry, text.notice) }
        if (text.preface.isNotEmpty()) {
            // Birdy's own lines about the library, in the app's language (the licence stays as written).
            item(key = "preface") {
                LicenseParagraph(text.preface.map { stringResource(it) }.joinToString("\n"))
            }
        }
        itemsIndexed(text.paragraphs, key = { index, _ -> index }) { _, paragraph -> LicenseParagraph(paragraph) }
    }
}

@Composable
private fun LicenseParagraph(paragraph: String) {
    Text(
        text = linkifyUrls(paragraph),
        color = TextOnCreme,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/** Name, version and licence, what the entry covers, its website, copyright lines and notice. */
@Composable
private fun LicenseTextHeader(
    entry: LicenseEntry,
    notice: String?,
) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
        Text(
            text = licenseEntryName(entry),
            fontFamily = rememberDmSerifDisplay(),
            fontSize = 24.sp,
            color = TextOnCreme,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = licenseEntryDetail(entry), color = InkMuted, fontSize = 13.sp)
        if (entry.artifacts.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.license_text_includes, entry.artifacts.joinToString(", ")),
                color = InkMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        entry.url?.let { url ->
            val website = stringResource(Res.string.license_text_website)
            Text(
                text = linkify(website, listOf(TextLink(website, url))),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        entry.copyright.forEach { line ->
            Text(text = line, color = TextOnCreme, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        notice?.let {
            Text(
                text = linkifyUrls(it),
                color = TextOnCreme,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Box(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Hairline),
        )
    }
}
