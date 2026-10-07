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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_by
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_error
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_hero
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_intro
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_numbered
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_open_file
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_open_license
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_other_title
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_row_description
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_title
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_use_model_test
import birdy_bird_scanner.composeapp.generated.resources.photo_credits_use_premium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackTopBar
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.credits.CreditLinkStyles
import se.birdy.app.ui.credits.PhotoCreditForm
import se.birdy.app.ui.credits.licenseLabel
import se.birdy.app.ui.credits.photoCreditText
import se.birdy.app.ui.credits.photoCreditWords
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

/**
 * A photo the app ships outside the species photos, credited under "Övriga bilder" (release 1.3.0,
 * review of Task 7e-2): what it is used for, and its credit. Both are CC0 photos by Hobbyfotowiki
 * that are also species heroes; the app's other images (the bird of the icon and the splash, the
 * map pin) are Birdy's own. `OtherImagesTest` fails when a new image file is bundled without being
 * listed here or as Birdy's own.
 */
internal data class OtherImage(
    val use: StringResource,
    val file: String,
    val credit: PhotoCredit,
)

internal val OTHER_IMAGES =
    listOf(
        OtherImage(
            use = Res.string.photo_credits_use_premium,
            file = "composeApp/src/commonMain/composeResources/files/premium/great-tit-hero.jpg",
            credit =
                PhotoCredit(
                    speciesId = SpeciesId("Q25485"),
                    speciesName = "Parus major",
                    scientificName = "Parus major",
                    role = "other",
                    path = "other/premium/great-tit-hero.jpg",
                    license = "CC0",
                    author = "Hobbyfotowiki",
                    commonsFileName = "Great tit (Parus major), North Rhine-Westphalia.jpg",
                ),
        ),
        OtherImage(
            use = Res.string.photo_credits_use_model_test,
            file = "shared/ml/src/commonMain/composeResources/files/testdata/parity_Q180991.jpg",
            credit =
                PhotoCredit(
                    speciesId = SpeciesId("Q180991"),
                    speciesName = "Mergus merganser",
                    scientificName = "Mergus merganser",
                    role = "other",
                    path = "other/testdata/parity_Q180991.jpg",
                    license = "CC0",
                    author = "Hobbyfotowiki",
                    commonsFileName = "Goosander (Eurasian) (Mergus merganser).jpg",
                ),
        ),
    )

// The main photo first, then the others in file order ("secondary-2" before "secondary-10").
private val PHOTO_ORDER = compareBy<PhotoCredit>({ it.role != HERO_ROLE }, { it.path.length }, { it.path })

/**
 * Groups [credits] per species (A to Ö by name), the main photo first and then in file order. Each
 * name's sort key is computed once. Call it off the main thread: it sorts some 750 species.
 */
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
                }.map { group -> swedishSortKey(group.speciesName) to group }
                .sortedWith(compareBy({ it.first }, { it.second.speciesId.raw }))
                .map { it.second },
    )

/** The photo credits read from the species database, off the main thread. */
@Composable
fun PhotoCreditsRoute(
    repository: SpeciesRepository,
    locale: Locale,
    onBack: () -> Unit,
) {
    val credits by rememberLoadable(repository to locale) {
        val rows = repository.photoCredits(locale)
        withContext(Dispatchers.Default) { groupPhotoCredits(rows) }
    }
    PhotoCreditsScreen(state = credits, locale = locale, onBack = onBack)
}

/**
 * Bildkällor / Photo credits (release 1.3.0 Task 7e-2, legal review §2): every species photo in the
 * app with its photographer, licence and source, CC0 and public domain included, then the other
 * photos the app ships ([OTHER_IMAGES]). A row opens the
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
            BackTopBar(onBack = onBack) { CreditsTitle(stringResource(Res.string.photo_credits_title)) }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                Loadable.Loading -> JournalLoading()
                Loadable.Failed -> CreditsError(stringResource(Res.string.photo_credits_error))
                // The licence line's links (licence, Commons) open through onOpenUrl too.
                is Loadable.Loaded -> ProvideUrlOpener(onOpenUrl) { PhotoCreditList(state.value, locale, onOpenUrl) }
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
            item(key = "species:${group.speciesId.raw}") { SpeciesHeader(group.speciesName, group.scientificName) }
            itemsIndexed(group.photos, key = { _, photo -> photo.path }) { index, photo ->
                val label =
                    if (photo.role == HERO_ROLE) {
                        stringResource(Res.string.photo_credits_hero)
                    } else {
                        stringResource(Res.string.photo_credits_numbered, (index + 1).toString())
                    }
                PhotoCreditRow(credit = photo, label = label, onOpenUrl = onOpenUrl)
            }
        }
        item(key = "other") { SpeciesHeader(stringResource(Res.string.photo_credits_other_title), null) }
        items(OTHER_IMAGES, key = { it.credit.path }) { image ->
            // Named like the species in the list above, in the reader's language.
            val name = credits.groups.firstOrNull { it.speciesId == image.credit.speciesId }?.speciesName
            PhotoCreditRow(
                credit = image.credit.copy(speciesName = name ?: image.credit.scientificName),
                label = stringResource(image.use),
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

@Composable
private fun SpeciesHeader(
    title: String,
    scientificName: String?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 2.dp)
                .semantics(mergeDescendants = true) { heading() },
    ) {
        Text(
            text = title,
            fontFamily = rememberDmSerifDisplay(),
            fontSize = 19.sp,
            color = TextOnCreme,
        )
        if (scientificName != null) {
            Text(text = scientificName, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = InkMuted)
        }
    }
}

@Composable
private fun PhotoCreditRow(
    credit: PhotoCredit,
    label: String,
    onOpenUrl: (String) -> Unit,
) {
    val words = photoCreditWords()
    val description =
        stringResource(
            Res.string.photo_credits_row_description,
            credit.speciesName,
            label,
            credit.author,
            licenseLabel(credit.license, words),
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
        // The same credit as on the species page (ui.credits), without "Foto: X", shown above.
        Text(
            text = photoCreditText(credit, words, PhotoCreditForm.LicenseLine, CreditLinkStyles),
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
