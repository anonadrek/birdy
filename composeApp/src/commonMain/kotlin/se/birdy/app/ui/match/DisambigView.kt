package se.birdy.app.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_db
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_frame_unavailable
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_photo
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_storage
import birdy_bird_scanner.composeapp.generated.resources.disambig_cancel_cta
import birdy_bird_scanner.composeapp.generated.resources.disambig_candidate_confidence
import birdy_bird_scanner.composeapp.generated.resources.disambig_eyebrow_one
import birdy_bird_scanner.composeapp.generated.resources.disambig_eyebrow_three
import birdy_bird_scanner.composeapp.generated.resources.disambig_eyebrow_two
import birdy_bird_scanner.composeapp.generated.resources.disambig_frame_caption
import birdy_bird_scanner.composeapp.generated.resources.disambig_headline
import birdy_bird_scanner.composeapp.generated.resources.disambig_pick_hint
import birdy_bird_scanner.composeapp.generated.resources.disambig_save_unknown
import birdy_bird_scanner.composeapp.generated.resources.disambig_sub
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BirdyTextButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri
import se.birdy.content.SpeciesId

// Disabled candidate card (already-picked-away sibling once one candidate is chosen): dimmed
// but still legible — same visual language as BirdyTextButton's DISABLED_ALPHA (0.45f), a touch
// lighter since a whole card reads as "disabled" more readily than a small text button does.
private const val DISABLED_CARD_ALPHA = 0.5f

@Composable
internal fun DisambigView(
    state: MatchResultUiState.Disambig,
    onPick: (SpeciesId) -> Unit,
    onSaveAsUnknown: () -> Unit,
    onUnknownSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val isSaving = state.saveStatus == MatchResultUiState.SaveStatus.Saving
    val isSaved = state.saveStatus == MatchResultUiState.SaveStatus.Saved

    SaveStatusEffects(
        saveStatus = state.saveStatus,
        snackbarHost = snackbarHost,
        onUnknownSaved = onUnknownSaved,
    )

    val eyebrowRes = disambigEyebrowRes(state.candidates.size)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            JournalIntro(
                label = stringResource(eyebrowRes, state.stampNumber),
                headline = stringResource(Res.string.disambig_headline),
                sub = stringResource(Res.string.disambig_sub),
                headlineFontSize = 30.sp,
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (state.frameJpegPath != null) {
                    FrameThumbnail(frameJpegPath = state.frameJpegPath)
                }

                state.candidates.forEach { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        enabled = !isSaving && !isSaved,
                        onClick = { onPick(candidate.species.id) },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                DisambigFooter(
                    isSaving = isSaving,
                    isSaved = isSaved,
                    onSaveAsUnknown = onSaveAsUnknown,
                    onCancel = onCancel,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SaveStatusEffects(
    saveStatus: MatchResultUiState.SaveStatus,
    snackbarHost: SnackbarHostState,
    onUnknownSaved: () -> Unit,
) {
    val errorPhotoLabel = stringResource(Res.string.diary_save_error_photo)
    val errorStorageLabel = stringResource(Res.string.diary_save_error_storage)
    val errorDbLabel = stringResource(Res.string.diary_save_error_db)
    val errorFrameLabel = stringResource(Res.string.diary_save_error_frame_unavailable)

    LaunchedEffect(saveStatus) {
        when (saveStatus) {
            MatchResultUiState.SaveStatus.Saved -> onUnknownSaved()
            is MatchResultUiState.SaveStatus.Failed ->
                snackbarHost.showSnackbar(
                    when (saveStatus.kind) {
                        MatchResultUiState.SaveStatus.Failed.Kind.PhotoEncodeFailed -> errorPhotoLabel
                        MatchResultUiState.SaveStatus.Failed.Kind.StorageFull -> errorStorageLabel
                        MatchResultUiState.SaveStatus.Failed.Kind.DatabaseFailed -> errorDbLabel
                        MatchResultUiState.SaveStatus.Failed.Kind.FrameUnavailable -> errorFrameLabel
                    },
                )
            else -> Unit
        }
    }
}

@Composable
private fun FrameThumbnail(frameJpegPath: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SandCreme)
                    .border(
                        width = 1.dp,
                        color = Hairline,
                        shape = RoundedCornerShape(16.dp),
                    ),
        ) {
            AsyncImage(
                model = "file://$frameJpegPath",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(Res.string.disambig_frame_caption),
            color = MarginaliaInk,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun DisambigFooter(
    isSaving: Boolean,
    isSaved: Boolean,
    onSaveAsUnknown: () -> Unit,
    onCancel: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(Res.string.disambig_pick_hint),
        color = InkMuted,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
    BirdyTextButton(
        text = stringResource(Res.string.disambig_save_unknown),
        onClick = onSaveAsUnknown,
        enabled = !isSaving && !isSaved,
        loading = isSaving,
        modifier = Modifier.fillMaxWidth(),
    )
    BirdyTextButton(
        text = stringResource(Res.string.disambig_cancel_cta),
        onClick = onCancel,
        enabled = !isSaving,
        color = InkMuted,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun CandidateCard(
    candidate: ResolvedPrediction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    val pct = (candidate.confidence * 100).toInt()
    val confidenceLabel = stringResource(Res.string.disambig_candidate_confidence, candidate.species.scientificName, "$pct%")
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else DISABLED_CARD_ALPHA)
                .clip(shape)
                .background(CardPaper)
                .border(width = 1.dp, color = Hairline, shape = shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val heroImage =
            candidate.species.images.firstOrNull { it.role == "hero" }
                ?: candidate.species.images.firstOrNull()
        CandidateThumbnail(heroImage?.path)
        Spacer(Modifier.size(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = candidate.species.name, color = TextOnCreme, fontFamily = serif, fontSize = 18.sp)
            Text(text = confidenceLabel, color = InkMuted, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkMuted)
    }
}

@Composable
private fun CandidateThumbnail(imagePath: String?) {
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SandCreme),
    ) {
        if (imagePath != null) {
            AsyncImage(
                model = speciesImageUri(imagePath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * The eyebrow names how many candidates are actually shown. Disambig keeps only the candidates
 * above the disambig threshold, so one card is possible; it used to say "two candidates" then.
 */
internal fun disambigEyebrowRes(candidateCount: Int): StringResource =
    when {
        candidateCount >= 3 -> Res.string.disambig_eyebrow_three
        candidateCount == 2 -> Res.string.disambig_eyebrow_two
        else -> Res.string.disambig_eyebrow_one
    }
