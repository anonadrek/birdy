package se.birdy.app.ui.settings.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.about_back
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_by
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_error
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_hero
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_intro
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_numbered
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_open_file
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_open_license
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_resized
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_row_description
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_title
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.settings.formatCount
import se.birdy.app.ui.settings.openExternalUrl
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.PhotoCredit
import se.birdy.content.search.swedishSortKey

/** One species' photos in the "Bildkällor" list, the main photo first. */
internal data class PhotoCreditGroup(
    val speciesId: SpeciesId,
    val speciesName: String,
    val scientificName: String,
    val photos: List<PhotoCredit>,
)

/** Everything the "Bildkällor" page lists: [total] photos in [groups], species in A to Ö order. */
internal data class PhotoCredits(
    val total: Int,
    val groups: List<PhotoCreditGroup>,
)

private const val HERO_ROLE = "hero"
private const val COMMONS = "Wikimedia Commons"

// The main photo first, then the others in file order ("secondary-2" before "secondary-10").
private val PHOTO_ORDER = compareBy<PhotoCredit>({ it.role != HERO_ROLE }, { it.path.length }, { it.path })

/** Groups [credits] per species (A to Ö by name), the main photo first and then in file order. */
internal fun groupPhotoCredits(credits: List<PhotoCredit>): PhotoCredits =
    PhotoCredits(
        total = credits.size,
        groups =
            credits
                .groupBy { it.speciesId }
                .map { (id, photos) ->
                    PhotoCreditGroup(
                        speciesId = id,
                        speciesName = photos.first().speciesName,
                        scientificName = photos.first().scientificName,
                        photos = photos.sortedWith(PHOTO_ORDER),
                    )
                }.sortedWith(compareBy({ swedishSortKey(it.speciesName) }, { it.speciesId.raw })),
    )

/** The photo credits read from the species database, off the main thread. */
@Composable
fun PhotoCreditsRoute(
    repository: SpeciesRepository,
    locale: Locale,
    onBack: () -> Unit,
) {
    val credits by rememberLoadable(repository to locale) { groupPhotoCredits(repository.photoCredits(locale)) }
    PhotoCreditsScreen(state = credits, locale = locale, onBack = onBack)
}

/**
 * Bildkällor / Photo credits (release 1.3.0 Task 7e-2, legal review §2): every species photo in the
 * app with its photographer, licence and source, CC0 and public domain included. A row opens the
 * photo's page on Wikimedia Commons; its licence name opens the licence. For TalkBack each row is
 * one node that reads the whole credit, with opening the licence as an extra action.
 */
@Composable
internal fun PhotoCreditsScreen(
    state: Loadable<PhotoCredits>,
    locale: Locale,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit = ::openExternalUrl,
) {
    JournalScaffold(
        topBar = {
            CreditsTopBar(
                onBack = onBack,
                backDescription = stringResource(Res.string.about_back),
                title = stringResource(Res.string.photo_credits_title),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                Loadable.Loading -> JournalLoading()
                Loadable.Failed -> CreditsError(stringResource(Res.string.photo_credits_error))
                is Loadable.Loaded -> PhotoCreditList(state.value, locale, onOpenUrl)
            }
        }
    }
}

@Composable
private fun PhotoCreditList(
    credits: PhotoCredits,
    locale: Locale,
    onOpenUrl: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp),
    ) {
        item(key = "intro") {
            Text(
                text = stringResource(Res.string.photo_credits_intro, formatCount(credits.total, locale)),
                color = InkMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        credits.groups.forEach { group ->
            item(key = "species:${group.speciesId.raw}") { SpeciesHeader(group) }
            itemsIndexed(group.photos, key = { _, photo -> photo.path }) { index, photo ->
                PhotoCreditRow(credit = photo, position = index + 1, onOpenUrl = onOpenUrl)
            }
        }
    }
}

@Composable
private fun SpeciesHeader(group: PhotoCreditGroup) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 2.dp)
                .semantics(mergeDescendants = true) { heading() },
    ) {
        Text(
            text = group.speciesName,
            fontFamily = rememberDmSerifDisplay(),
            fontSize = 19.sp,
            color = TextOnCreme,
        )
        Text(text = group.scientificName, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = InkMuted)
    }
}

@Composable
private fun PhotoCreditRow(
    credit: PhotoCredit,
    position: Int,
    onOpenUrl: (String) -> Unit,
) {
    val label =
        if (credit.role == HERO_ROLE) {
            stringResource(Res.string.photo_credits_hero)
        } else {
            stringResource(Res.string.photo_credits_numbered, position.toString())
        }
    val description =
        stringResource(
            Res.string.photo_credits_row_description,
            credit.speciesName,
            label,
            credit.author,
            credit.license,
        )
    val openFileLabel = stringResource(Res.string.photo_credits_open_file)
    val licenseUrl = credit.licenseUrl
    val openLicenseLabel = stringResource(Res.string.photo_credits_open_license, credit.license)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = openFileLabel) { onOpenUrl(credit.filePageUrl) }
                .clearAndSetSemantics {
                    contentDescription = description
                    onClick(label = openFileLabel) {
                        onOpenUrl(credit.filePageUrl)
                        true
                    }
                    if (licenseUrl != null) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(openLicenseLabel) {
                                    onOpenUrl(licenseUrl)
                                    true
                                },
                            )
                    }
                }.padding(vertical = 8.dp),
    ) {
        Text(text = label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.W600, color = InkMuted)
        Text(text = stringResource(Res.string.photo_credits_by, credit.author), fontSize = 14.sp, color = TextOnCreme)
        Text(
            text = licenseLine(credit.license, licenseUrl, stringResource(Res.string.photo_credits_resized), onOpenUrl),
            fontSize = 13.sp,
            color = InkMuted,
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Hairline),
        )
    }
}

/**
 * "CC BY 2.0 · Wikimedia Commons · nedskalad" as one text, so that it wraps as a sentence at large
 * text sizes. The licence is a link of its own when it has a deed; a tap anywhere else on the row
 * opens the photo.
 */
private fun licenseLine(
    license: String,
    licenseUrl: String?,
    resized: String,
    onOpenUrl: (String) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        if (licenseUrl != null) {
            val link =
                LinkAnnotation.Clickable(
                    tag = "license",
                    styles = CreditLinkStyles,
                    linkInteractionListener = { onOpenUrl(licenseUrl) },
                )
            withLink(link) { append(license) }
        } else {
            append(license)
        }
        append(" · $COMMONS · $resized")
    }
