package se.birdy.app.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_unknown_species
import birdy_bird_scanner.composeapp.generated.resources.recap_a11y_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_cta_open_camera
import birdy_bird_scanner.composeapp.generated.resources.recap_delta_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_empty_plate
import birdy_bird_scanner.composeapp.generated.resources.recap_eyebrow_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_headline_active
import birdy_bird_scanner.composeapp.generated.resources.recap_headline_quiet
import birdy_bird_scanner.composeapp.generated.resources.recap_load_error
import birdy_bird_scanner.composeapp.generated.resources.recap_new_badge_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_new_species_tag
import birdy_bird_scanner.composeapp.generated.resources.recap_quiet_encouragement
import birdy_bird_scanner.composeapp.generated.resources.recap_stats_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_streak_label
import birdy_bird_scanner.composeapp.generated.resources.recap_streak_nudge_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_summary_active_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_summary_active_new
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.OrnamentRule
import se.birdy.app.ui.components.PlateFrame
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.util.speciesImageUri

@Composable
fun RecapScreen(
    viewModel: RecapViewModel,
    onOpenCamera: () -> Unit,
    onObservationClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize().paperBackground()) {
        when (val s = state) {
            RecapUiState.Loading -> JournalLoading()
            is RecapUiState.Loaded -> RecapContent(s, onOpenCamera, onObservationClick)
            is RecapUiState.Error -> RecapError()
        }
    }
}

@Composable
private fun RecapError() {
    val caveat = rememberCaveat()
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.recap_load_error),
            color = MarginaliaInk,
            fontFamily = caveat,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecapContent(
    state: RecapUiState.Loaded,
    onOpenCamera: () -> Unit,
    onObservationClick: (String) -> Unit,
) {
    val summary = state.recap.summary
    val a11yLabel = stringResource(Res.string.recap_a11y_fmt, summary.week.isoWeek.toString())
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .semantics { contentDescription = a11yLabel },
    ) {
        Text(
            stringResource(Res.string.recap_eyebrow_fmt, summary.week.isoWeek.toString()),
            color = AccentCopper,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        JournalHeadline(
            text =
                stringResource(
                    if (summary.isQuiet) Res.string.recap_headline_quiet else Res.string.recap_headline_active,
                ),
            fontSize = 32.sp,
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (summary.isQuiet) {
            QuietBody(state, onOpenCamera)
        } else {
            ActiveBody(state, onObservationClick)
        }
    }
}

@Composable
private fun ActiveBody(
    state: RecapUiState.Loaded,
    onObservationClick: (String) -> Unit,
) {
    val s = state.recap.summary
    val caveat = rememberCaveat()

    Text(
        if (s.newSpeciesCount > 0) {
            stringResource(Res.string.recap_summary_active_new, s.observationCount.toString())
        } else {
            stringResource(Res.string.recap_summary_active_fmt, s.observationCount.toString())
        },
        color = MarginaliaInk,
        fontFamily = caveat,
        fontSize = 21.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    OrnamentRule()
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        stringResource(
            Res.string.recap_stats_fmt,
            s.observationCount.toString(),
            s.newSpeciesCount.toString(),
            s.weeklyStreak.toString(),
        ),
        color = AccentCopper,
        fontFamily = caveat,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    state.newBadgeNames.forEach { name ->
        Text(
            stringResource(Res.string.recap_new_badge_fmt, name),
            color = MarginaliaInk,
            fontFamily = caveat,
            fontSize = 18.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    Text(
        stringResource(Res.string.recap_delta_fmt, formatSigned(s.deltaVsLastWeek)),
        color = MarginaliaInk,
        fontFamily = caveat,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 10.dp),
    )

    // Veckans alla fynd, nyaste först — staplade plåtar.
    Spacer(modifier = Modifier.height(20.dp))
    state.finds.forEach { find ->
        FindPlate(find = find, onObservationClick = onObservationClick)
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun FindPlate(
    find: RecapFindItem,
    onObservationClick: (String) -> Unit,
) {
    val caption = find.speciesName ?: stringResource(Res.string.diary_detail_unknown_species)
    val model: String? =
        if (find.photoPath.isNotBlank()) {
            "file://${find.photoPath}"
        } else {
            find.heroImagePath?.let { speciesImageUri(it) }
        }
    PlateFrame(
        plateLabel = "",
        captionLine = caption,
        modifier = Modifier.clickable { onObservationClick(find.observationId) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "❦",
                    color = MarginaliaInk.copy(alpha = 0.35f),
                    fontSize = 48.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (find.isNewSpecies) {
                Text(
                    text = stringResource(Res.string.recap_new_species_tag).uppercase(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 1.2.sp,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentCopper)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun QuietBody(
    state: RecapUiState.Loaded,
    onOpenCamera: () -> Unit,
) {
    val s = state.recap.summary
    val caveat = rememberCaveat()
    val emptyCaption = stringResource(Res.string.recap_empty_plate)

    PlateFrame(
        plateLabel = "",
        captionLine = emptyCaption,
    ) {
        // Empty plate — show ❦ ornament centred (PlateFrame supplies its own border)
        Text(
            text = "❦",
            color = MarginaliaInk.copy(alpha = 0.35f),
            fontSize = 48.sp,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        stringResource(Res.string.recap_quiet_encouragement),
        color = MarginaliaInk,
        fontFamily = caveat,
        fontSize = 21.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    OrnamentRule()
    if (s.streakAtRisk) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(Res.string.recap_streak_label),
            color = AccentCopper,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
        )
        Text(
            stringResource(Res.string.recap_streak_nudge_fmt, s.weeklyStreak.toString()),
            color = MarginaliaInk,
            fontFamily = caveat,
            fontSize = 19.sp,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        stringResource(Res.string.recap_cta_open_camera),
        color = AccentCopper,
        fontSize = 14.sp,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenCamera)
                .padding(12.dp),
        textAlign = TextAlign.Center,
    )
}

private fun formatSigned(n: Int): String = if (n >= 0) "+$n" else "$n"
