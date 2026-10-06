package se.birdy.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badge_common
import birdy_bird_scanner.composeapp.generated.resources.badge_uncommon
import birdy_bird_scanner.composeapp.generated.resources.empty_description
import birdy_bird_scanner.composeapp.generated.resources.empty_migration
import birdy_bird_scanner.composeapp.generated.resources.empty_photos
import birdy_bird_scanner.composeapp.generated.resources.not_found_body
import birdy_bird_scanner.composeapp.generated.resources.not_found_title
import birdy_bird_scanner.composeapp.generated.resources.premium_species_subtitle
import birdy_bird_scanner.composeapp.generated.resources.premium_species_title
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_corner
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_cta
import birdy_bird_scanner.composeapp.generated.resources.profile_back
import birdy_bird_scanner.composeapp.generated.resources.profile_iucn_description
import birdy_bird_scanner.composeapp.generated.resources.profile_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.profile_label_description
import birdy_bird_scanner.composeapp.generated.resources.profile_label_migration
import birdy_bird_scanner.composeapp.generated.resources.profile_label_photos
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.components.HeroImage
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.PaperSheet
import se.birdy.app.ui.components.PaperSheetOverlap
import se.birdy.app.ui.components.PhotoHero
import se.birdy.app.ui.components.PremiumTeaserCard
import se.birdy.app.ui.encyclopedia.localizedFamilyLabel
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaBorder
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.model.Species

@Composable
fun SpeciesProfileScreen(
    viewModel: SpeciesProfileViewModel,
    locale: Locale,
    onBack: () -> Unit,
    onPremiumClick: () -> Unit,
    showPremiumTeaser: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        SpeciesProfileUiState.Loading -> JournalLoading()
        SpeciesProfileUiState.NotFound ->
            EmptyState(
                title = stringResource(Res.string.not_found_title),
                body = stringResource(Res.string.not_found_body),
            )
        is SpeciesProfileUiState.Loaded -> ProfileContent(s.species, locale, onBack, onPremiumClick, showPremiumTeaser)
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun ProfileContent(
    species: Species,
    locale: Locale,
    onBack: () -> Unit,
    onPremiumClick: () -> Unit,
    showPremiumTeaser: Boolean,
) {
    val serif = rememberDmSerifDisplay()
    val familyLabel = localizedFamilyLabel(locale, species.taxonomy.family, species.taxonomy.familySv)
    val heroImage = species.images.firstOrNull { it.role == "hero" } ?: species.images.firstOrNull()
    // The family lives ONLY in the kicker now — the quality review found it shown twice (kicker
    // + an abundance-adjacent pill in ProfilePillRow, removed below). SV pairs the Swedish family
    // name with the Latin one ("Mesar · Paridae") when both exist and differ; EN shows the family
    // alone (there is no separate English trivial name in the content, see localizedFamilyLabel).
    // familySv is hoisted to a local val: species.taxonomy.familySv is a public property from a
    // different Gradle module, so a chained null-check on the property access itself can't be
    // smart-cast (cross-module properties may have custom getters) — a local val can.
    val familySv = species.taxonomy.familySv
    val kicker =
        if (locale == Locale.SV && !familySv.isNullOrBlank() && familySv != species.taxonomy.family) {
            stringResource(Res.string.profile_journal_sub, familySv, species.taxonomy.family)
        } else {
            familyLabel
        }

    LazyColumn(modifier = Modifier.fillMaxSize().background(MossCreme)) {
        item {
            PhotoHero(
                kicker = kicker,
                title = species.name,
                latinName = species.scientificName,
                height = 320.dp,
                bottomPadding = PaperSheetOverlap + 18.dp,
                drawBehindStatusBar = true,
                image =
                    heroImage?.let { img ->
                        {
                            AsyncImage(
                                model = speciesImageUri(img.path),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    },
                topBar = {
                    BackButton(
                        onClick = onBack,
                        contentDescription = stringResource(Res.string.profile_back),
                        onDark = true,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                },
                bottomContent = {
                    Spacer(Modifier.height(10.dp))
                    ProfilePillRow(species = species)
                },
            )
        }

        item {
            PaperSheet {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MicroLabel(stringResource(Res.string.profile_label_description))
                    Spacer(Modifier.height(8.dp))
                    DescriptionWithDropCap(
                        text = species.description.orEmpty().ifBlank { stringResource(Res.string.empty_description) },
                        serif = serif,
                    )
                }

                if (!species.marginalia.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    MarginaliaBlock(text = species.marginalia!!)
                }

                if (showPremiumTeaser) {
                    Spacer(Modifier.height(16.dp))
                    PremiumTeaserCard(
                        title = stringResource(Res.string.premium_species_title),
                        subtitle = stringResource(Res.string.premium_species_subtitle),
                        cornerLabel = stringResource(Res.string.premium_teaser_corner),
                        ctaLabel = stringResource(Res.string.premium_teaser_cta),
                        onUnlock = onPremiumClick,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    MicroLabel(stringResource(Res.string.profile_label_migration))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = species.migration.orEmpty().ifBlank { stringResource(Res.string.empty_migration) },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = TextOnCreme,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    MicroLabel(stringResource(Res.string.profile_label_photos))
                    Spacer(Modifier.height(8.dp))
                    if (species.images.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.empty_photos),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MarginaliaInk,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (img in species.images.take(3)) {
                                HeroImage(
                                    imagePath = img.path,
                                    modifier = Modifier.weight(1f).height(64.dp),
                                    cornerRadius = 12.dp,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * The abundance/IUCN pill row, drawn in [PhotoHero]'s bottomContent slot — light-on-dark, over
 * the photo's text-following scrim (spec 2026-09-24 §4.3). The family already has its own place
 * in the kicker above (see [ProfileContent]'s `kicker`), so it does not get a pill here too —
 * the quality review caught the family appearing twice. [FlowRow] wraps a long IUCN label onto a
 * second line instead of overflowing the hero's width; `semantics(mergeDescendants = true)` on
 * the row groups the pills into one accessible stop instead of two-to-three separate ones (the
 * IUCN pill's own [JournalPill] uses `clearAndSetSemantics` rather than another
 * `mergeDescendants = true`, so it does not become a second, un-merged merge boundary).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfilePillRow(species: Species) {
    val abundanceLabel =
        when (species.abundance) {
            Abundance.ALLMÄN -> stringResource(Res.string.badge_common)
            else -> stringResource(Res.string.badge_uncommon)
        }
    // The pill alone ("Livskraftig"/"Least concern") gives no hint this is an IUCN rating, let
    // alone the GLOBAL red list rather than a national one — the quality review flagged the
    // missing context. The mapped word gets its code appended ("Livskraftig (LC)"); an
    // unmapped/unknown status falls back to the raw code alone (no word to pair it with).
    val iucnCode = species.iucnStatus.uppercase()
    val iucnWord = iucnStatusLabel(iucnCode)?.let { stringResource(it) }
    val iucnPillText = iucnWord?.let { "$it ($iucnCode)" } ?: species.iucnStatus

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        JournalPill(text = abundanceLabel, isFilled = true)
        if (iucnPillText.isNotBlank()) {
            JournalPill(
                text = iucnPillText,
                isFilled = false,
                contentDescription = stringResource(Res.string.profile_iucn_description, iucnPillText),
            )
        }
    }
}

@Composable
private fun DescriptionWithDropCap(
    text: String,
    serif: FontFamily,
) {
    // trimStart only strips leading whitespace, so a leading space doesn't become the drop cap
    // itself. The repository already strips markdown (headings, bold/italic markers) before this
    // screen ever sees the text — see se.birdy.content.cleanSpeciesText.
    val trimmed = text.trimStart()
    val firstChar = trimmed.first().toString()
    val rest = trimmed.drop(1)
    val annotated =
        AnnotatedString
            .Builder()
            .apply {
                pushStyle(
                    SpanStyle(
                        fontFamily = serif,
                        fontSize = 44.sp,
                        fontStyle = FontStyle.Italic,
                        color = AccentCopper,
                    ),
                )
                append(firstChar)
                pop()
                pushStyle(SpanStyle(fontSize = 14.5.sp))
                append(rest)
                pop()
            }.toAnnotatedString()
    Text(
        text = annotated,
        color = TextOnCreme,
        lineHeight = 22.sp,
    )
}

@Composable
private fun MarginaliaBlock(text: String) {
    val caveat = rememberCaveat()
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier =
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MarginaliaBorder),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = MarginaliaInk,
            fontFamily = caveat,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
        )
    }
}

// Glass fill alpha for the profile hero's unfilled pills — pinned by ProfilePillContrastTest,
// change both together.
internal const val PROFILE_PILL_GLASS_ALPHA = 0.16f

@Composable
private fun JournalPill(
    text: String,
    isFilled: Boolean,
    contentDescription: String? = null,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (isFilled) AccentCopper else Color.White.copy(alpha = PROFILE_PILL_GLASS_ALPHA))
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .let { m ->
                    // clearAndSetSemantics, not semantics(mergeDescendants = true): the latter
                    // would make this pill its OWN merge boundary, so the parent row's
                    // mergeDescendants (ProfilePillRow) could not merge it in — TalkBack would
                    // get two stops (row, then pill) instead of one, and might still read the
                    // Text's literal contentDescription-less-node text too. clearAndSetSemantics
                    // replaces the Text's literal-text semantics with this description and is
                    // NOT a merge boundary itself, so the row merges it into its single stop.
                    if (contentDescription != null) {
                        m.clearAndSetSemantics { this.contentDescription = contentDescription }
                    } else {
                        m
                    }
                },
    ) {
        Text(
            text = text,
            color = TextOnHero,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.W600,
            fontSize = 10.sp,
            lineHeight = 12.sp,
        )
    }
}
