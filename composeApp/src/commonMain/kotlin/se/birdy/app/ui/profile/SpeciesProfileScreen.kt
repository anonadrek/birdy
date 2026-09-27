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
import birdy_bird_scanner.composeapp.generated.resources.iucn_cr
import birdy_bird_scanner.composeapp.generated.resources.iucn_dd
import birdy_bird_scanner.composeapp.generated.resources.iucn_en
import birdy_bird_scanner.composeapp.generated.resources.iucn_lc
import birdy_bird_scanner.composeapp.generated.resources.iucn_nt
import birdy_bird_scanner.composeapp.generated.resources.iucn_vu
import birdy_bird_scanner.composeapp.generated.resources.not_found_body
import birdy_bird_scanner.composeapp.generated.resources.not_found_title
import birdy_bird_scanner.composeapp.generated.resources.premium_species_subtitle
import birdy_bird_scanner.composeapp.generated.resources.premium_species_title
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_corner
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_cta
import birdy_bird_scanner.composeapp.generated.resources.profile_back
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

    LazyColumn(modifier = Modifier.fillMaxSize().background(MossCreme)) {
        item {
            PhotoHero(
                kicker = familyLabel,
                title = species.name,
                latinName = species.scientificName,
                height = 320.dp,
                bottomPadding = PaperSheetOverlap + 18.dp,
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
                    ProfilePillRow(species = species, familyLabel = familyLabel)
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
 * The abundance/family/IUCN pill row, drawn in [PhotoHero]'s bottomContent slot — light-on-dark,
 * over the photo's text-following scrim (spec 2026-09-24 §4.3). [FlowRow] wraps a long family
 * name or IUCN label onto a second line instead of overflowing the hero's width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfilePillRow(
    species: Species,
    familyLabel: String,
) {
    val abundanceLabel =
        when (species.abundance) {
            Abundance.ALLMÄN -> stringResource(Res.string.badge_common)
            else -> stringResource(Res.string.badge_uncommon)
        }
    val iucnLabel =
        when (species.iucnStatus.uppercase()) {
            "LC" -> stringResource(Res.string.iucn_lc)
            "NT" -> stringResource(Res.string.iucn_nt)
            "VU" -> stringResource(Res.string.iucn_vu)
            "EN" -> stringResource(Res.string.iucn_en)
            "CR" -> stringResource(Res.string.iucn_cr)
            "DD" -> stringResource(Res.string.iucn_dd)
            else -> species.iucnStatus
        }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        JournalPill(text = abundanceLabel, isFilled = true)
        JournalPill(text = familyLabel, isFilled = false)
        if (iucnLabel.isNotBlank()) JournalPill(text = iucnLabel, isFilled = false)
    }
}

@Composable
private fun DescriptionWithDropCap(
    text: String,
    serif: FontFamily,
) {
    val firstChar = text.first().toString()
    val rest = text.drop(1)
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
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (isFilled) AccentCopper else Color.White.copy(alpha = PROFILE_PILL_GLASS_ALPHA))
                .padding(horizontal = 10.dp, vertical = 4.dp),
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
