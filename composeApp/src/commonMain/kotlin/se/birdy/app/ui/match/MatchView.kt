package se.birdy.app.ui.match

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_db
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_frame_unavailable
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_photo
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_storage
import birdy_bird_scanner.composeapp.generated.resources.diary_save_success
import birdy_bird_scanner.composeapp.generated.resources.match_cancel_cta
import birdy_bird_scanner.composeapp.generated.resources.match_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.match_headline
import birdy_bird_scanner.composeapp.generated.resources.match_marginalia_captured_audio
import birdy_bird_scanner.composeapp.generated.resources.match_marginalia_captured_photo
import birdy_bird_scanner.composeapp.generated.resources.match_marginalia_first_sighting
import birdy_bird_scanner.composeapp.generated.resources.match_marginalia_manual_pick
import birdy_bird_scanner.composeapp.generated.resources.match_marginalia_repeat
import birdy_bird_scanner.composeapp.generated.resources.match_note_label
import birdy_bird_scanner.composeapp.generated.resources.match_save_cta
import birdy_bird_scanner.composeapp.generated.resources.match_saved_text
import birdy_bird_scanner.composeapp.generated.resources.match_stamp_caption_pending
import birdy_bird_scanner.composeapp.generated.resources.match_sub_confidence
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.badges.BadgeStringMap
import se.birdy.app.ui.badges.UnlockBottomSheet
import se.birdy.app.ui.components.BodyTextWithCaveatAccents
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.PlateFrame
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.content.Locale
import se.birdy.ml.ScanSource

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun MatchView(
    state: MatchResultUiState.Match,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    onDismissUnlock: () -> Unit,
    locale: Locale,
    zone: TimeZone,
) {
    val snackbarHost = remember { SnackbarHostState() }
    var note by rememberSaveable { mutableStateOf("") }
    val caveatFamily = rememberCaveat()
    val successLabel = stringResource(Res.string.diary_save_success)
    val errorPhotoLabel = stringResource(Res.string.diary_save_error_photo)
    val errorStorageLabel = stringResource(Res.string.diary_save_error_storage)
    val errorDbLabel = stringResource(Res.string.diary_save_error_db)
    val errorFrameLabel = stringResource(Res.string.diary_save_error_frame_unavailable)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.saveStatus) {
        when (val s = state.saveStatus) {
            MatchResultUiState.SaveStatus.Saved -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHost.showSnackbar(successLabel)
            }
            is MatchResultUiState.SaveStatus.Failed ->
                snackbarHost.showSnackbar(
                    when (s.kind) {
                        MatchResultUiState.SaveStatus.Failed.Kind.PhotoEncodeFailed -> errorPhotoLabel
                        MatchResultUiState.SaveStatus.Failed.Kind.StorageFull -> errorStorageLabel
                        MatchResultUiState.SaveStatus.Failed.Kind.DatabaseFailed -> errorDbLabel
                        MatchResultUiState.SaveStatus.Failed.Kind.FrameUnavailable -> errorFrameLabel
                    },
                )
            else -> Unit
        }
    }

    val confidencePct = (state.confidence * 100).toInt()
    val isSaved = state.saveStatus == MatchResultUiState.SaveStatus.Saved
    val isSaving = state.saveStatus == MatchResultUiState.SaveStatus.Saving

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            JournalIntro(
                label = stringResource(Res.string.match_eyebrow, state.stampNumber),
                headline = stringResource(Res.string.match_headline, state.species.name),
                sub = stringResource(Res.string.match_sub_confidence, "$confidencePct%"),
                headlineFontSize = 36.sp,
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                PlateFrame(
                    plateLabel = state.stampNumber.toString(),
                    captionLine = state.species.scientificName,
                ) {
                    val heroImage =
                        state.species.images.firstOrNull { it.role == "hero" }
                            ?: state.species.images.firstOrNull()
                    if (heroImage != null) {
                        AsyncImage(
                            model = Res.getUri("files/images/${heroImage.path}"),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    StampWithAnimation(state = state)
                }

                Spacer(Modifier.height(16.dp))

                MatchMarginalia(state = state, zone = zone)

                Spacer(Modifier.height(24.dp))

                if (isSaved) {
                    Text(
                        text = stringResource(Res.string.match_saved_text),
                        color = MarginaliaInk,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                } else {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = {
                            Text(
                                text = stringResource(Res.string.match_note_label),
                                fontFamily = caveatFamily,
                            )
                        },
                        singleLine = true,
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSave(note.trim()) }),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCopper,
                                cursorColor = AccentCopper,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onSave(note.trim()) },
                        enabled = !isSaving && !isSaved && state.unlockQueueSize == 0,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = OffwhiteWarm),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = OffwhiteWarm,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.match_save_cta),
                                fontWeight = FontWeight.W600,
                                fontSize = 16.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onCancel,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(Res.string.match_cancel_cta),
                            color = MarginaliaInk,
                            fontSize = 14.sp,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        val pendingBadge = state.pendingBadge
        val pendingUnlock = state.pendingUnlock
        if (pendingBadge != null && pendingUnlock != null) {
            UnlockBottomSheet(
                badge = pendingBadge,
                unlockedAt = pendingUnlock.unlockedAt,
                isCelebration = true,
                locale = locale,
                zone = zone,
                nameRes = BadgeStringMap.nameFor(pendingBadge.id),
                descriptionRes = BadgeStringMap.descriptionFor(pendingBadge.id),
                onDismiss = onDismissUnlock,
            )
        }
    }
}

@Composable
private fun StampWithAnimation(state: MatchResultUiState.Match) {
    val isSaved = state.saveStatus == MatchResultUiState.SaveStatus.Saved
    val captionPending = stringResource(Res.string.match_stamp_caption_pending)
    val scale by animateFloatAsState(
        targetValue = if (isSaved) 1f else 1f,
        animationSpec = tween(durationMillis = 320),
        label = "stamp-scale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (isSaved && state.isFirstSighting) -4f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "stamp-rotation",
    )
    val alpha by animateFloatAsState(
        targetValue =
            if (isSaved) {
                1f
            } else if (state.isFirstSighting) {
                0.4f
            } else {
                0.85f
            },
        animationSpec = tween(durationMillis = 320),
        label = "stamp-alpha",
    )

    val stampState: StampSealState =
        when {
            isSaved ->
                StampSealState.Unlocked(
                    number = state.stampNumber,
                    glyph = null,
                    name = if (state.isFirstSighting) state.species.name else null,
                )
            state.isFirstSighting ->
                StampSealState.Locked(name = captionPending)
            else ->
                StampSealState.InProgress(
                    number = state.stampNumber,
                    name = null,
                    progressLabel = null,
                )
        }

    Box(modifier = Modifier.scale(scale).rotate(rotation)) {
        StampSeal(
            state = stampState,
            size = 110.dp,
            modifier = Modifier.scale(1f).rotate(0f),
        )
    }
    // Note: alpha used inline by StampSeal-color choice; LinearProgressIndicator wraps
    // it during Saving state below.
    if (state.saveStatus == MatchResultUiState.SaveStatus.Saving) {
        CircularProgressIndicator(
            color = AccentCopper,
            strokeWidth = 2.dp,
            modifier = Modifier.size(40.dp),
        )
    }
    // alpha gate is implicit through StampSealState choice (Locked→dashed/ghost,
    // Unlocked→solid). The animated `alpha` value documents intent for future tweaks
    // when StampSeal exposes an opacity hook.
    @Suppress("UnusedVariable")
    val unused = alpha
}

@Composable
private fun MatchMarginalia(
    state: MatchResultUiState.Match,
    zone: TimeZone,
) {
    val caveat = rememberCaveat()
    val capturedLabel =
        when (state.source) {
            is ScanSource.Audio -> stringResource(Res.string.match_marginalia_captured_audio)
            is ScanSource.Image -> stringResource(Res.string.match_marginalia_captured_photo)
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = capturedLabel,
            fontFamily = caveat,
            color = MarginaliaInk,
            fontStyle = FontStyle.Italic,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
        if (state.isFirstSighting) {
            BodyTextWithCaveatAccents(
                text = stringResource(Res.string.match_marginalia_first_sighting),
                accentColor = AccentCopper,
                plainColor = TextOnCreme,
                fontSize = 18.sp,
            )
        } else {
            val prev = state.prevObservedAt
            val dateLabel =
                if (prev != null) {
                    val dt = prev.toLocalDateTime(zone)
                    val month = monthShortUppercase(dt.monthNumber)
                    "${dt.dayOfMonth} $month ${dt.year}"
                } else {
                    "—"
                }
            BodyTextWithCaveatAccents(
                text = stringResource(Res.string.match_marginalia_repeat, state.sightingCount, dateLabel),
                accentColor = AccentCopper,
                plainColor = TextOnCreme,
                fontSize = 13.sp,
            )
        }
        if (state.isManualPick) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = stringResource(Res.string.match_marginalia_manual_pick).removeSurrounding("*"),
                    color = AccentCopper,
                    fontFamily = caveat,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                    modifier = Modifier.rotate(-6f),
                )
            }
        }
    }
}
