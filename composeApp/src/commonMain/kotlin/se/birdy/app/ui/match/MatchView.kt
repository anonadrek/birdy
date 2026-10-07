package se.birdy.app.ui.match

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
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
import se.birdy.app.ui.components.BirdyPrimaryButton
import se.birdy.app.ui.components.BirdyTextButton
import se.birdy.app.ui.components.BodyTextWithCaveatAccents
import se.birdy.app.ui.components.PaperSheet
import se.birdy.app.ui.components.PaperSheetOverlap
import se.birdy.app.ui.components.PhotoBackButton
import se.birdy.app.ui.components.PhotoHero
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.OutlineInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.util.speciesImageUri
import se.birdy.content.Locale
import se.birdy.ml.ScanSource

// Caps the stamp column's width so a long caption (StampSeal's 10sp name/caption text has no
// maxLines — "awaiting your signature", or a long species name after a first-sighting save) wraps
// instead of crushing the weighted marginalia column at large font scales (a Row measures its
// unweighted children — this Box — before it hands the rest of the width to weight(1f)).
private val StampColumnMaxWidth = 104.dp

// The Match photo sits above the name (PhotoHero's textBelowPhoto), which makes the hero taller,
// and "Spara observation" below it is this screen's main action: it must be in view without
// scrolling, also with larger text (2026-10-06). So the photo takes the room that is left above
// everything that follows it, between MATCH_PHOTO_MIN and MATCH_PHOTO_MAX. MATCH_BELOW_PHOTO is
// what follows the photo down to the bottom of the save button (name, latin name, match bar,
// paper sheet with stamp, note field, button) at 100% text on a 360dp-wide phone, and it grows by
// MATCH_BELOW_PHOTO_PER_FONT_SCALE per +1.0 of font scale (measured 2026-10-06: 379dp, +88.5dp).
// MatchSaveButtonFoldTest checks the result on a 360x800dp phone at 100%, 130% and 200% text;
// re-measure there if the content below the photo changes.
internal val MATCH_PHOTO_MAX = 260.dp
internal val MATCH_PHOTO_MIN = 160.dp
private val MATCH_BELOW_PHOTO = 380.dp
private val MATCH_BELOW_PHOTO_PER_FONT_SCALE = 90.dp
private val MATCH_FOLD_MARGIN = 8.dp

/**
 * The Match photo's height for a screen whose visible area is [viewportHeight] tall (the hero
 * draws behind the [statusBar], so that height comes off the top) at [fontScale].
 */
internal fun matchPhotoHeight(
    viewportHeight: Dp,
    statusBar: Dp,
    fontScale: Float,
): Dp {
    val belowPhoto = MATCH_BELOW_PHOTO + MATCH_BELOW_PHOTO_PER_FONT_SCALE * (fontScale - 1f).coerceAtLeast(0f)
    return (viewportHeight - statusBar - belowPhoto - MATCH_FOLD_MARGIN).coerceIn(MATCH_PHOTO_MIN, MATCH_PHOTO_MAX)
}

@Composable
private fun rememberMatchPhotoHeight(viewportHeight: Dp): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fontScale = LocalDensity.current.fontScale
    return remember(viewportHeight, statusBar, fontScale) { matchPhotoHeight(viewportHeight, statusBar, fontScale) }
}

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val photoHeight = rememberMatchPhotoHeight(viewportHeight = maxHeight)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // PaperSheet's rounded top corners overlap the hero below; this must match
                    // its own MossCreme fill or a seam of a different color shows through the
                    // corner cutouts (see PaperSheet's KDoc).
                    .background(MossCreme)
                    .verticalScroll(rememberScrollState()),
        ) {
            val heroImage =
                state.species.images.firstOrNull { it.role == "hero" }
                    ?: state.species.images.firstOrNull()
            val heroPath = heroImage?.path
            val confidenceLabel = stringResource(Res.string.match_sub_confidence, "$confidencePct%")

            PhotoHero(
                kicker = stringResource(Res.string.match_eyebrow, state.stampNumber),
                title = state.species.name,
                latinName = state.species.scientificName,
                // The photo sits above the name, so the whole bird is in view (2026-10-06); its
                // height gives way so the save button stays in view (see matchPhotoHeight).
                height = photoHeight,
                bottomPadding = PaperSheetOverlap + 18.dp,
                drawBehindStatusBar = true,
                textBelowPhoto = true,
                image =
                    heroPath?.let { path ->
                        {
                            AsyncImage(
                                model = speciesImageUri(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                bottomContent = {
                    ConfidenceBar(confidence = state.confidence, label = confidenceLabel)
                },
            )
            PaperSheet {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MatchMarginalia(state = state, zone = zone, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.widthIn(max = StampColumnMaxWidth)) {
                        StampWithAnimation(state = state, size = 72.dp)
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (isSaved) {
                    Text(
                        text = stringResource(Res.string.match_saved_text),
                        color = InkMuted,
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
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = OutlineInk,
                                focusedBorderColor = AccentCopper,
                                unfocusedContainerColor = CardPaper,
                                focusedContainerColor = CardPaper,
                                cursorColor = AccentCopper,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    BirdyPrimaryButton(
                        text = stringResource(Res.string.match_save_cta),
                        onClick = { onSave(note.trim()) },
                        enabled = !isSaving && !isSaved && state.unlockQueueSize == 0,
                        loading = isSaving,
                        leadingIcon = Icons.Outlined.Check,
                    )
                    Spacer(Modifier.height(8.dp))
                    BirdyTextButton(
                        text = stringResource(Res.string.match_cancel_cta),
                        onClick = onCancel,
                        enabled = !isSaving,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
        // Fixed over the scrolling photo and sheet: after "Spara" the "Avbryt" button is gone and
        // this is the way back (release 1.3.0 Task 7b). Disabled while saving, like "Avbryt"
        // (MatchResultScreen swallows the back gesture then).
        PhotoBackButton(onBack = onCancel, enabled = !isSaving)
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

/** Confidence bar drawn in [PhotoHero]'s bottomContent slot (light-on-dark, spec 2026-09-24 §4.3). */
@Composable
private fun ConfidenceBar(
    confidence: Float,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Box(
            Modifier
                .width(72.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(TextOnHero.copy(alpha = 0.2f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(confidence.coerceIn(0f, 1f))
                    .background(AccentCopperLight),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextOnHero, fontSize = 12.sp, fontWeight = FontWeight.W600)
    }
}

@Composable
private fun StampWithAnimation(
    state: MatchResultUiState.Match,
    size: Dp,
) {
    val isSaved = state.saveStatus == MatchResultUiState.SaveStatus.Saved
    val captionPending = stringResource(Res.string.match_stamp_caption_pending)
    val rotation by animateFloatAsState(
        targetValue = if (isSaved && state.isFirstSighting) -4f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "stamp-rotation",
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

    Box(modifier = Modifier.rotate(rotation)) {
        StampSeal(
            state = stampState,
            size = size,
        )
    }
}

@Composable
private fun MatchMarginalia(
    state: MatchResultUiState.Match,
    zone: TimeZone,
    modifier: Modifier = Modifier,
) {
    val caveat = rememberCaveat()
    val capturedLabel =
        when (state.source) {
            is ScanSource.Audio -> stringResource(Res.string.match_marginalia_captured_audio)
            is ScanSource.Image -> stringResource(Res.string.match_marginalia_captured_photo)
        }
    Column(modifier = modifier.fillMaxWidth()) {
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
                    text = stringResource(Res.string.match_marginalia_manual_pick),
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
