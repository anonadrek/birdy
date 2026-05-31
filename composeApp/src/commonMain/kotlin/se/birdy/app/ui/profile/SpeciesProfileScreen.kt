package se.birdy.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.em
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
import birdy_bird_scanner.composeapp.generated.resources.profile_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.profile_journal_label
import birdy_bird_scanner.composeapp.generated.resources.profile_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.profile_label_description
import birdy_bird_scanner.composeapp.generated.resources.profile_label_migration
import birdy_bird_scanner.composeapp.generated.resources.profile_label_photos
import birdy_bird_scanner.composeapp.generated.resources.profile_plate_caption
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.components.HeroImage
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.PlateFrame
import se.birdy.app.ui.components.PremiumTeaserCard
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaBorder
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri
import se.birdy.content.Abundance
import se.birdy.content.model.Species

@Composable
fun SpeciesProfileScreen(
    viewModel: SpeciesProfileViewModel,
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
        is SpeciesProfileUiState.Loaded -> ProfileContent(s.species, onBack, onPremiumClick, showPremiumTeaser)
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun ProfileContent(
    species: Species,
    onBack: () -> Unit,
    onPremiumClick: () -> Unit,
    showPremiumTeaser: Boolean,
) {
    val serif = rememberDmSerifDisplay()
    val plateLabel = species.id.raw.removePrefix("Q")
    val captionPart = stringResource(Res.string.profile_plate_caption)
    val familyLabel = species.taxonomy.familySv ?: species.taxonomy.family

    Box(modifier = Modifier.fillMaxSize().paperBackground()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box {
                    JournalIntro(
                        label = stringResource(Res.string.profile_journal_label, plateLabel),
                        headline = stringResource(Res.string.profile_journal_headline, species.name),
                        sub = stringResource(Res.string.profile_journal_sub, species.scientificName, familyLabel),
                        headlineFontSize = 36.sp,
                    )
                    BackButton(
                        onClick = onBack,
                        contentDescription = stringResource(Res.string.profile_back),
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 24.dp, start = 12.dp),
                    )
                }
            }

            item {
                val heroImage = species.images.firstOrNull { it.role == "hero" } ?: species.images.firstOrNull()
                if (heroImage != null) {
                    PlateFrame(
                        plateLabel = plateLabel,
                        captionLine = "${species.name}, $captionPart",
                    ) {
                        AsyncImage(
                            model = speciesImageUri(heroImage.path),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val abundanceLabel =
                        when (species.abundance) {
                            Abundance.ALLMÄN -> stringResource(Res.string.badge_common)
                            else -> stringResource(Res.string.badge_uncommon)
                        }
                    JournalPill(text = abundanceLabel, isFilled = true)
                    JournalPill(text = familyLabel, isFilled = false)
                    JournalPill(text = species.iucnStatus, isFilled = false)
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SectionLabel(stringResource(Res.string.profile_label_description))
                    Spacer(Modifier.height(6.dp))
                    DescriptionWithDropCap(
                        text = species.description.orEmpty().ifBlank { stringResource(Res.string.empty_description) },
                        serif = serif,
                    )
                }
            }

            if (!species.marginalia.isNullOrBlank()) {
                item { MarginaliaBlock(text = species.marginalia!!) }
            }

            if (showPremiumTeaser) {
                item {
                    PremiumTeaserCard(
                        title = stringResource(Res.string.premium_species_title),
                        subtitle = stringResource(Res.string.premium_species_subtitle),
                        cornerLabel = stringResource(Res.string.premium_teaser_corner),
                        ctaLabel = stringResource(Res.string.premium_teaser_cta),
                        onUnlock = onPremiumClick,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SectionLabel(stringResource(Res.string.profile_label_migration))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = species.migration.orEmpty().ifBlank { stringResource(Res.string.empty_migration) },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = TextOnCreme,
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SectionLabel(stringResource(Res.string.profile_label_photos))
                    Spacer(Modifier.height(6.dp))
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
                                    cornerRadius = 8.dp,
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MarginaliaInk,
        fontSize = 9.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.22.em,
    )
}

@Composable
private fun DescriptionWithDropCap(
    text: String,
    serif: FontFamily,
) {
    if (text.isEmpty()) {
        Text(text = "", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val firstChar = text.first().toString()
    val rest = text.drop(1)
    val annotated =
        AnnotatedString
            .Builder()
            .apply {
                pushStyle(
                    SpanStyle(
                        fontFamily = serif,
                        fontSize = 26.sp,
                        fontStyle = FontStyle.Italic,
                        color = AccentCopper,
                    ),
                )
                append(firstChar)
                pop()
                pushStyle(SpanStyle(fontSize = 13.sp))
                append(rest)
                pop()
            }.toAnnotatedString()
    Text(
        text = annotated,
        color = TextOnCreme,
        lineHeight = 20.sp,
    )
}

@Composable
private fun MarginaliaBlock(text: String) {
    val caveat = rememberCaveat()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(2.dp)
                    .height(48.dp)
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

@Composable
private fun JournalPill(
    text: String,
    isFilled: Boolean,
) {
    val serif = rememberDmSerifDisplay()
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (isFilled) AccentCopper else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = AccentCopper.copy(alpha = if (isFilled) 0f else 0.5f),
                    shape = RoundedCornerShape(50),
                ).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (isFilled) OffwhiteWarm else TextOnCreme,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontSize = 11.sp,
        )
    }
}
