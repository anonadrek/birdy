package se.birdy.app.ui.settings.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.licenses_entry_google
import birdy_bird_scanner.composeapp.generated.resources.licenses_entry_native
import birdy_bird_scanner.composeapp.generated.resources.licenses_error
import birdy_bird_scanner.composeapp.generated.resources.licenses_intro
import birdy_bird_scanner.composeapp.generated.resources.licenses_open
import birdy_bird_scanner.composeapp.generated.resources.licenses_row_version
import birdy_bird_scanner.composeapp.generated.resources.licenses_section_fonts
import birdy_bird_scanner.composeapp.generated.resources.licenses_section_google
import birdy_bird_scanner.composeapp.generated.resources.licenses_section_libraries
import birdy_bird_scanner.composeapp.generated.resources.licenses_section_models
import birdy_bird_scanner.composeapp.generated.resources.licenses_section_tensorflow
import birdy_bird_scanner.composeapp.generated.resources.licenses_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackTopBar
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.TextOnCreme

/** The localized title of an index.json section, or null for an id the app does not know. */
internal fun licenseSectionTitle(id: String): StringResource? =
    when (id) {
        "models" -> Res.string.licenses_section_models
        "fonts" -> Res.string.licenses_section_fonts
        "tensorflow" -> Res.string.licenses_section_tensorflow
        "google" -> Res.string.licenses_section_google
        "libraries" -> Res.string.licenses_section_libraries
        else -> null
    }

/** The ids of the two entries that collect notices rather than name one library. */
internal const val NATIVE_NOTICES_ID = "tensorflow-native"
internal const val GOOGLE_NOTICES_ID = "google-third-party"

/** An entry's name: the app's own words for the two notice collections, else the library's name. */
@Composable
internal fun licenseEntryName(entry: LicenseEntry): String =
    when (entry.id) {
        NATIVE_NOTICES_ID -> stringResource(Res.string.licenses_entry_native, entry.artifacts.size.toString())
        GOOGLE_NOTICES_ID -> stringResource(Res.string.licenses_entry_google)
        else -> entry.name
    }

/** "Version 1.8.2 · Apache License 2.0", or only the licences for an entry without a version. */
@Composable
internal fun licenseEntryDetail(entry: LicenseEntry): String =
    if (entry.version.isBlank()) {
        entry.license
    } else {
        "${stringResource(Res.string.licenses_row_version, entry.version)} · ${entry.license}"
    }

/** The licence list read from the bundled index. */
@Composable
fun OpenSourceLicensesRoute(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val index by rememberLoadable(Unit) { LicenseFiles.index() }
    OpenSourceLicensesScreen(state = index, onBack = onBack, onOpenEntry = onOpenEntry)
}

/**
 * Licenser för öppen källkod / Open-source licenses (release 1.3.0, legal review 7i-fix B): the
 * models, fonts, TensorFlow and its native code, Google's libraries and every other library in the
 * release build, each with its version and licence. A row opens the full text.
 */
@Composable
internal fun OpenSourceLicensesScreen(
    state: Loadable<LicenseIndex>,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    JournalScaffold(
        topBar = {
            BackTopBar(onBack = onBack) { CreditsTitle(stringResource(Res.string.licenses_title)) }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                Loadable.Loading -> JournalLoading()
                Loadable.Failed -> CreditsError(stringResource(Res.string.licenses_error))
                is Loadable.Loaded -> LicenseList(state.value, onOpenEntry)
            }
        }
    }
}

@Composable
private fun LicenseList(
    index: LicenseIndex,
    onOpenEntry: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp),
    ) {
        item(key = "intro") {
            Text(
                text = stringResource(Res.string.licenses_intro),
                color = InkMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
        index.sections.forEach { section ->
            val title = licenseSectionTitle(section.id) ?: return@forEach
            item(key = "section:${section.id}") {
                MicroLabel(
                    text = stringResource(title),
                    modifier = Modifier.padding(top = 22.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            items(section.entries, key = { it.id }) { entry -> LicenseRow(entry, onOpenEntry) }
        }
    }
}

@Composable
private fun LicenseRow(
    entry: LicenseEntry,
    onOpenEntry: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(Res.string.licenses_open)) { onOpenEntry(entry.id) }
                .padding(top = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = licenseEntryName(entry),
                    color = TextOnCreme,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W500,
                )
                Text(text = licenseEntryDetail(entry), color = InkMuted, fontSize = 12.sp)
                if (entry.artifacts.isNotEmpty()) {
                    Text(
                        text = entry.artifacts.joinToString(", "),
                        color = InkMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Text("›", color = AccentCopper, fontSize = 18.sp, fontWeight = FontWeight.W600)
        }
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Hairline),
        )
    }
}
