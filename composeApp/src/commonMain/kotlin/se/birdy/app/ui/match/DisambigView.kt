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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.disambig_cancel_cta
import birdy_bird_scanner.composeapp.generated.resources.disambig_candidate_confidence
import birdy_bird_scanner.composeapp.generated.resources.disambig_eyebrow_three
import birdy_bird_scanner.composeapp.generated.resources.disambig_eyebrow_two
import birdy_bird_scanner.composeapp.generated.resources.disambig_frame_caption
import birdy_bird_scanner.composeapp.generated.resources.disambig_headline
import birdy_bird_scanner.composeapp.generated.resources.disambig_pick_hint
import birdy_bird_scanner.composeapp.generated.resources.disambig_save_unknown
import birdy_bird_scanner.composeapp.generated.resources.disambig_sub
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri
import se.birdy.content.SpeciesId

@Composable
internal fun DisambigView(
    state: MatchResultUiState.Disambig,
    onPick: (SpeciesId) -> Unit,
    onSaveAsUnknown: () -> Unit,
    onCancel: () -> Unit,
) {
    val eyebrowRes =
        if (state.candidates.size >= 3) {
            Res.string.disambig_eyebrow_three
        } else {
            Res.string.disambig_eyebrow_two
        }
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
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.4f))
                                    .border(
                                        width = 1.dp,
                                        color = AccentCopper.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(10.dp),
                                    ),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(Res.string.disambig_frame_caption).removeSurrounding("*"),
                            color = MarginaliaInk,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                state.candidates.forEach { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        onClick = { onPick(candidate.species.id) },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.disambig_pick_hint),
                    color = MarginaliaInk.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                TextButton(
                    onClick = onSaveAsUnknown,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.disambig_save_unknown),
                        color = AccentCopper,
                        fontWeight = FontWeight.W600,
                        fontSize = 14.sp,
                    )
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.disambig_cancel_cta),
                        color = MarginaliaInk,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun CandidateCard(
    candidate: ResolvedPrediction,
    onClick: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val pct = (candidate.confidence * 100).toInt()
    val confidenceLabel = stringResource(Res.string.disambig_candidate_confidence, candidate.species.scientificName, "$pct%")
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.4f))
                .border(
                    width = 1.dp,
                    color = AccentCopper.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                ).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val heroImage =
            candidate.species.images.firstOrNull { it.role == "hero" }
                ?: candidate.species.images.firstOrNull()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
        ) {
            if (heroImage != null) {
                AsyncImage(
                    model = speciesImageUri(heroImage.path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(
            text = candidate.species.name,
            color = TextOnCreme,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 20.sp,
        )
        Text(
            text = confidenceLabel,
            color = AccentCopper,
            fontFamily = caveat,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}
