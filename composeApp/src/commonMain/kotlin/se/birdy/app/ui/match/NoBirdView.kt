package se.birdy.app.ui.match

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.audio_no_bird_hint_1
import birdy_bird_scanner.composeapp.generated.resources.audio_no_bird_hint_2
import birdy_bird_scanner.composeapp.generated.resources.audio_no_bird_hint_3
import birdy_bird_scanner.composeapp.generated.resources.nobird_date_format
import birdy_bird_scanner.composeapp.generated.resources.nobird_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.nobird_headline
import birdy_bird_scanner.composeapp.generated.resources.nobird_retry_cta
import birdy_bird_scanner.composeapp.generated.resources.nobird_sub
import birdy_bird_scanner.composeapp.generated.resources.nobird_tip_center
import birdy_bird_scanner.composeapp.generated.resources.nobird_tip_center_sub
import birdy_bird_scanner.composeapp.generated.resources.nobird_tip_closer
import birdy_bird_scanner.composeapp.generated.resources.nobird_tip_closer_sub
import birdy_bird_scanner.composeapp.generated.resources.nobird_tip_light
import birdy_bird_scanner.composeapp.generated.resources.nobird_tip_light_sub
import birdy_bird_scanner.composeapp.generated.resources.nobird_top_guess_hint
import coil3.compose.AsyncImage
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BodyTextWithCaveatAccents
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.ml.ScanSource

@Composable
internal fun NoBirdView(
    state: MatchResultUiState.NoBird,
    onRetry: () -> Unit,
    zone: TimeZone,
) {
    val dt = Instant.fromEpochMilliseconds(state.capturedAtMs).toLocalDateTime(zone)
    val month = monthShortUppercase(dt.monthNumber)
    val dateLabel = stringResource(Res.string.nobird_date_format, dt.dayOfMonth, month, dt.year)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            JournalIntro(
                label = stringResource(Res.string.nobird_eyebrow, dateLabel),
                headline = stringResource(Res.string.nobird_headline),
                sub = stringResource(Res.string.nobird_sub),
                headlineFontSize = 28.sp,
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (state.frameJpegPath != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(120.dp)
                                    .rotate(-3f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.4f))
                                    .border(
                                        width = 1.dp,
                                        color = AccentCopper.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                        ) {
                            AsyncImage(
                                model = "file://${state.frameJpegPath}",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                if (state.source is ScanSource.Audio) {
                    AudioMarginaliaTipsBlock()
                } else {
                    MarginaliaTipsBlock()
                }
                state.topPrediction?.let { p ->
                    Spacer(Modifier.height(12.dp))
                    val pct = (p.confidence * 100).toInt()
                    BodyTextWithCaveatAccents(
                        text = stringResource(Res.string.nobird_top_guess_hint, p.species.name, "$pct%"),
                        accentColor = AccentCopper,
                        plainColor = MarginaliaInk,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = OffwhiteWarm),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.nobird_retry_cta),
                        fontWeight = FontWeight.W600,
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AudioMarginaliaTipsBlock() {
    Column(modifier = Modifier.fillMaxWidth()) {
        TipLine(
            text = stringResource(Res.string.audio_no_bird_hint_1),
            subText = "",
            rotationDeg = -2f,
            alignmentEndDp = 0,
        )
        Spacer(Modifier.height(8.dp))
        TipLine(
            text = stringResource(Res.string.audio_no_bird_hint_2),
            subText = "",
            rotationDeg = 3f,
            alignmentEndDp = 48,
        )
        Spacer(Modifier.height(8.dp))
        TipLine(
            text = stringResource(Res.string.audio_no_bird_hint_3),
            subText = "",
            rotationDeg = -4f,
            alignmentEndDp = 16,
        )
    }
}

@Composable
private fun MarginaliaTipsBlock() {
    Column(modifier = Modifier.fillMaxWidth()) {
        TipLine(
            text = stringResource(Res.string.nobird_tip_closer),
            subText = stringResource(Res.string.nobird_tip_closer_sub),
            rotationDeg = -2f,
            alignmentEndDp = 0,
        )
        Spacer(Modifier.height(8.dp))
        TipLine(
            text = stringResource(Res.string.nobird_tip_center),
            subText = stringResource(Res.string.nobird_tip_center_sub),
            rotationDeg = 3f,
            alignmentEndDp = 48,
        )
        Spacer(Modifier.height(8.dp))
        TipLine(
            text = stringResource(Res.string.nobird_tip_light),
            subText = stringResource(Res.string.nobird_tip_light_sub),
            rotationDeg = -4f,
            alignmentEndDp = 16,
        )
    }
}

@Composable
private fun TipLine(
    text: String,
    subText: String,
    rotationDeg: Float,
    alignmentEndDp: Int,
) {
    val caveat = rememberCaveat()
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = alignmentEndDp.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = subText,
            color = MarginaliaInk.copy(alpha = 0.7f),
            fontSize = 11.sp,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            color = MarginaliaInk,
            fontFamily = caveat,
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp,
            modifier = Modifier.rotate(rotationDeg),
        )
    }
}
