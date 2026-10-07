package se.birdy.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.about_back
import birdy_bird_scanner.composeapp.generated.resources.about_credits_label
import birdy_bird_scanner.composeapp.generated.resources.about_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.about_headline_accent_1
import birdy_bird_scanner.composeapp.generated.resources.about_headline_plain_1
import birdy_bird_scanner.composeapp.generated.resources.about_headline_plain_2
import birdy_bird_scanner.composeapp.generated.resources.about_licenses_label
import birdy_bird_scanner.composeapp.generated.resources.about_map_body
import birdy_bird_scanner.composeapp.generated.resources.about_map_title
import birdy_bird_scanner.composeapp.generated.resources.about_names_body
import birdy_bird_scanner.composeapp.generated.resources.about_names_title
import birdy_bird_scanner.composeapp.generated.resources.about_photo_id_body
import birdy_bird_scanner.composeapp.generated.resources.about_photo_id_title
import birdy_bird_scanner.composeapp.generated.resources.about_photos_body
import birdy_bird_scanner.composeapp.generated.resources.about_photos_body_uncounted
import birdy_bird_scanner.composeapp.generated.resources.about_photos_title
import birdy_bird_scanner.composeapp.generated.resources.about_row_licenses
import birdy_bird_scanner.composeapp.generated.resources.about_row_licenses_sub
import birdy_bird_scanner.composeapp.generated.resources.about_row_photo_credits
import birdy_bird_scanner.composeapp.generated.resources.about_row_photo_credits_sub
import birdy_bird_scanner.composeapp.generated.resources.about_sound_id_body
import birdy_bird_scanner.composeapp.generated.resources.about_sound_id_title
import birdy_bird_scanner.composeapp.generated.resources.about_subline
import birdy_bird_scanner.composeapp.generated.resources.about_texts_body
import birdy_bird_scanner.composeapp.generated.resources.about_texts_title
import birdy_bird_scanner.composeapp.generated.resources.about_version_prefix
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.settings.credits.CreditsTopBar
import se.birdy.app.ui.settings.credits.Loadable
import se.birdy.app.ui.settings.credits.ProvideUrlOpener
import se.birdy.app.ui.settings.credits.TextLink
import se.birdy.app.ui.settings.credits.linkify
import se.birdy.app.ui.settings.credits.rememberLoadable
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.content.Locale
import se.birdy.content.SpeciesRepository

/** A paragraph of About's "Innehåll & data": its title, its text and the names in it that link. */
internal data class CreditParagraph(
    val title: StringResource,
    val body: StringResource,
    val links: List<TextLink>,
)

/**
 * Where the app's content comes from and under which licences (release 1.3.0, legal review 7i-fix C,
 * the proposal in docs/legal/2026-10-1.3.0-genomgang.md §5). The phrases are names, the same in
 * both languages; `AboutCreditsTest` checks that each is in the Swedish and the English text.
 */
internal object AboutCredits {
    const val CC_BY_SA_4 = "https://creativecommons.org/licenses/by-sa/4.0/"
    const val CC_BY_3 = "https://creativecommons.org/licenses/by/3.0/"
    const val CC0 = "https://creativecommons.org/publicdomain/zero/1.0/"
    const val CC_BY_NC_SA_4 = "https://creativecommons.org/licenses/by-nc-sa/4.0/"
    const val APACHE_2 = "https://www.apache.org/licenses/LICENSE-2.0"

    /** The photos paragraph, which also carries the number of photos (see [AboutScreen]). */
    val photoLinks = listOf(TextLink("Wikimedia Commons", "https://commons.wikimedia.org/"))

    val paragraphs =
        listOf(
            CreditParagraph(
                Res.string.about_texts_title,
                Res.string.about_texts_body,
                listOf(TextLink("Wikipedia", "https://www.wikipedia.org/"), TextLink("CC BY-SA 4.0", CC_BY_SA_4)),
            ),
            CreditParagraph(
                Res.string.about_names_title,
                Res.string.about_names_body,
                listOf(
                    TextLink("IOC World Bird List v14.1", "https://www.worldbirdnames.org/"),
                    TextLink("CC BY 3.0", CC_BY_3),
                    TextLink("Wikidata", "https://www.wikidata.org/"),
                    TextLink("CC0", CC0),
                ),
            ),
            CreditParagraph(
                Res.string.about_map_title,
                Res.string.about_map_body,
                listOf(
                    TextLink("MapTiler", "https://www.maptiler.com/copyright/"),
                    TextLink("OpenStreetMap", "https://www.openstreetmap.org/copyright"),
                    TextLink("ODbL", "https://opendatacommons.org/licenses/odbl/"),
                ),
            ),
            CreditParagraph(
                Res.string.about_photo_id_title,
                Res.string.about_photo_id_body,
                listOf(TextLink("Apache License 2.0", APACHE_2)),
            ),
            CreditParagraph(
                Res.string.about_sound_id_title,
                Res.string.about_sound_id_body,
                listOf(
                    TextLink("BirdNET-Lite", "https://github.com/birdnet-team/BirdNET-Lite"),
                    TextLink("CC BY-NC-SA 4.0", CC_BY_NC_SA_4),
                    TextLink("Ecological Informatics 61: 101236", "https://doi.org/10.1016/j.ecoinf.2021.101236"),
                ),
            ),
        )
}

/**
 * [count] with grouped thousands as each language writes it: "2 066" (a no-break space) in
 * Swedish, "2,066" in English.
 */
internal fun formatCount(
    count: Int,
    locale: Locale,
): String {
    val separator = if (locale == Locale.SV) "\u00A0" else ","
    return count
        .toString()
        .reversed()
        .chunked(THOUSANDS)
        .joinToString(separator)
        .reversed()
}

private const val THOUSANDS = 3

/** About with the number of photos read from the species database. */
@Suppress("LongParameterList") // the database and language for the count, and the screen's own callbacks.
@Composable
fun AboutRoute(
    repository: SpeciesRepository,
    locale: Locale,
    version: String,
    onBack: () -> Unit,
    onOpenPhotoCredits: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val photoCount by rememberLoadable(repository) { repository.photoCount() }
    AboutScreen(
        onBack = onBack,
        version = version,
        photoCount = (photoCount as? Loadable.Loaded)?.value,
        locale = locale,
        onOpenPhotoCredits = onOpenPhotoCredits,
        onOpenLicenses = onOpenLicenses,
    )
}

/**
 * Om / About. "Innehåll & data" names every source and licence of the app's content (release 1.3.0,
 * legal review 7i-fix C); the rows below open the photo credits (Task 7e-2) and the open-source
 * licences (7i-fix B). [photoCount] is null until it has been read; links open in the browser
 * through [onOpenUrl].
 */
@Suppress("LongParameterList") // three destinations and the URL opener are separate callbacks, as on SettingsScreen.
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    version: String,
    photoCount: Int?,
    locale: Locale,
    onOpenPhotoCredits: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenUrl: (String) -> Unit = ::openExternalUrl,
) {
    ProvideUrlOpener(onOpenUrl) {
        JournalScaffold(
            topBar = { CreditsTopBar(onBack = onBack, backDescription = stringResource(Res.string.about_back)) },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                AboutHeader(version)
                Spacer(Modifier.height(24.dp))
                ContentAndData(photoCount, locale)
                Spacer(Modifier.height(24.dp))
                SourcesAndLicenses(onOpenPhotoCredits, onOpenLicenses)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun AboutHeader(version: String) {
    val headline =
        "${stringResource(Res.string.about_headline_plain_1)} " +
            "*${stringResource(Res.string.about_headline_accent_1)}* " +
            stringResource(Res.string.about_headline_plain_2)
    JournalIntro(
        label = stringResource(Res.string.about_eyebrow),
        headline = headline,
        sub = stringResource(Res.string.about_subline),
        horizontalPadding = 0,
        topPadding = 8,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "${stringResource(Res.string.about_version_prefix)} $version",
        fontFamily = rememberDmSerifDisplay(),
        fontStyle = FontStyle.Italic,
        fontSize = 18.sp,
        color = TextOnCreme,
    )
}

/** "Innehåll & data": every source of the app's content with its licence. */
@Composable
private fun ContentAndData(
    photoCount: Int?,
    locale: Locale,
) {
    MicroLabel(stringResource(Res.string.about_credits_label))
    Spacer(Modifier.height(4.dp))
    val photosBody =
        if (photoCount != null) {
            stringResource(Res.string.about_photos_body, formatCount(photoCount, locale))
        } else {
            stringResource(Res.string.about_photos_body_uncounted)
        }
    CreditBlock(stringResource(Res.string.about_photos_title), photosBody, AboutCredits.photoLinks)
    AboutCredits.paragraphs.forEach { paragraph ->
        CreditBlock(stringResource(paragraph.title), stringResource(paragraph.body), paragraph.links)
    }
}

/** "Källor & licenser": the rows to the photo credits and the open-source licences. */
@Composable
private fun SourcesAndLicenses(
    onOpenPhotoCredits: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    MicroLabel(stringResource(Res.string.about_licenses_label))
    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SandCreme),
    ) {
        AboutLinkRow(
            icon = Icons.Outlined.PhotoLibrary,
            title = stringResource(Res.string.about_row_photo_credits),
            sub = stringResource(Res.string.about_row_photo_credits_sub),
            onClick = onOpenPhotoCredits,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(MarginaliaInk.copy(alpha = 0.18f)),
        )
        AboutLinkRow(
            icon = Icons.Outlined.Description,
            title = stringResource(Res.string.about_row_licenses),
            sub = stringResource(Res.string.about_row_licenses_sub),
            onClick = onOpenLicenses,
        )
    }
}

@Composable
private fun CreditBlock(
    title: String,
    body: String,
    links: List<TextLink>,
) {
    Spacer(Modifier.height(14.dp))
    Text(
        text = title,
        color = TextOnCreme,
        fontSize = 15.sp,
        fontWeight = FontWeight.W600,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = linkify(body, links),
        color = InkMuted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    sub: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, AccentCopper, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AccentCopper, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextOnCreme, fontSize = 14.sp, fontWeight = FontWeight.W500)
            Text(text = sub, color = InkMuted, fontSize = 12.sp)
        }
        Text("›", color = AccentCopper, fontSize = 18.sp, fontWeight = FontWeight.W600)
    }
}
