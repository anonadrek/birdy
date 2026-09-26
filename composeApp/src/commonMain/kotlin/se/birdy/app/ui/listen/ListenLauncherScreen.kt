package se.birdy.app.ui.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_card_a11y
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_card_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_caught_today
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_eyebrow_breeding
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_eyebrow_migrating
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_eyebrow_present
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_not_caught
import birdy_bird_scanner.composeapp.generated.resources.gear_content_description
import birdy_bird_scanner.composeapp.generated.resources.listen_card_audio_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_audio_title
import birdy_bird_scanner.composeapp.generated.resources.listen_card_camera_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_camera_title
import birdy_bird_scanner.composeapp.generated.resources.listen_card_photo_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_photo_title
import birdy_bird_scanner.composeapp.generated.resources.listen_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.listen_journal_label
import birdy_bird_scanner.composeapp.generated.resources.listen_journal_sub
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.GearButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.PhotoHero
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperDeep
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri
import se.birdy.domain.dailybird.SeasonTag

@Composable
fun ListenLauncherScreen(
    viewModel: ListenLauncherViewModel,
    onCameraClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToAudioScan: () -> Unit,
    onSpeciesProfileClick: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ListenLauncherEffect.NavigateToAudioScan -> onNavigateToAudioScan()
            }
        }
    }

    JournalScaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            val dailyBirdState by viewModel.dailyBird.collectAsState()
            // Two variants, not one shared lambda: the hero is a dark photo (rust-on-dark-moss
            // is hard to see, needs the light onDark styling), the fallback sits on plain paper
            // (default rust ring is correct there).
            val gearOnHero: @Composable BoxScope.() -> Unit = {
                Box(Modifier.align(Alignment.TopEnd).padding(end = 16.dp)) {
                    GearButton(
                        onClick = onSettingsClick,
                        contentDescription = stringResource(Res.string.gear_content_description),
                        onDark = true,
                    )
                }
            }
            val gearOnPaper: @Composable BoxScope.() -> Unit = {
                Box(Modifier.align(Alignment.TopEnd).padding(end = 16.dp)) {
                    GearButton(
                        onClick = onSettingsClick,
                        contentDescription = stringResource(Res.string.gear_content_description),
                    )
                }
            }
            val ui = dailyBirdState
            if (ui != null) {
                DailyBirdHero(ui = ui, onClick = { onSpeciesProfileClick(ui.speciesId) }, topBar = gearOnHero)
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), content = gearOnPaper)
            }
            JournalIntro(
                label = stringResource(Res.string.listen_journal_label),
                headline = stringResource(Res.string.listen_journal_headline),
                sub = stringResource(Res.string.listen_journal_sub),
                topPadding = 20,
            )
            Column(modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
                LaunchCard(
                    icon = Icons.Filled.PhotoCamera,
                    title = stringResource(Res.string.listen_card_camera_title),
                    body = stringResource(Res.string.listen_card_camera_body),
                    variant = LaunchCardVariant.Primary,
                    onClick = onCameraClick,
                )
                Spacer(Modifier.height(8.dp))
                LaunchCard(
                    icon = Icons.Filled.PhotoLibrary,
                    title = stringResource(Res.string.listen_card_photo_title),
                    body = stringResource(Res.string.listen_card_photo_body),
                    variant = LaunchCardVariant.Secondary,
                    onClick = onPhotoClick,
                )
                LaunchCard(
                    icon = Icons.Filled.Hearing,
                    title = stringResource(Res.string.listen_card_audio_title),
                    body = stringResource(Res.string.listen_card_audio_body),
                    variant = LaunchCardVariant.Secondary,
                    onClick = viewModel::onAudioCardTap,
                )
            }
        }
    }
}

/**
 * Today's bird as the screen's [PhotoHero]: species name as the title, the season line as the
 * subtitle, caught-today status + progress ("x / target") as the meta row. Replaces the old
 * moss-card `DailyBirdCard` (spec 2026-09-24 §4.4.1). [topBar] renders the gear button so it
 * still reaches its own, independently focusable semantics node (see mergeDescendants note
 * below) even though it's drawn over the hero.
 */
@Composable
private fun DailyBirdHero(
    ui: ListenLauncherViewModel.DailyBirdUi,
    onClick: () -> Unit,
    topBar: @Composable BoxScope.() -> Unit,
) {
    val seasonText =
        stringResource(
            when (ui.seasonTag) {
                SeasonTag.BREEDING -> Res.string.daily_bird_eyebrow_breeding
                SeasonTag.PRESENT -> Res.string.daily_bird_eyebrow_present
                SeasonTag.MIGRATING -> Res.string.daily_bird_eyebrow_migrating
            },
        )
    val a11y = stringResource(Res.string.daily_bird_card_a11y, ui.name, seasonText)
    val caught =
        stringResource(if (ui.caughtToday) Res.string.daily_bird_caught_today else Res.string.daily_bird_not_caught)
    PhotoHero(
        kicker = stringResource(Res.string.daily_bird_card_eyebrow),
        title = ui.name,
        subtitle = seasonText,
        metaStart = caught,
        metaEnd = "${ui.matchCount} / ${ui.huntTarget}",
        height = 300.dp,
        // mergeDescendants collapses PhotoHero's own kicker/title/subtitle/meta text nodes into
        // this one contentDescription — but it does NOT swallow topBar's GearButton: clickable()
        // makes the gear its own merge boundary, so it stays independently focusable (verified
        // in IdentifyScreenshotTest via onNodeWithContentDescription + assertHasClickAction on
        // both nodes).
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = a11y },
        image =
            ui.heroImagePath?.let { path ->
                {
                    AsyncImage(
                        model = speciesImageUri(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        topBar = topBar,
    )
}

private enum class LaunchCardVariant { Primary, Secondary }

@Composable
private fun LaunchCard(
    icon: ImageVector,
    title: String,
    body: String,
    variant: LaunchCardVariant,
    onClick: () -> Unit,
) {
    val primary = variant == LaunchCardVariant.Primary
    val serif = rememberDmSerifDisplay()
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { m ->
                    if (primary) {
                        m
                            .shadow(6.dp, shape)
                            .clip(shape)
                            .background(Brush.linearGradient(listOf(AccentCopper, AccentCopperDeep)))
                    } else {
                        m.drawBehind {
                            drawLine(Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                        }
                    }
                }.clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = if (primary) 16.dp else 2.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (primary) TextOnHero.copy(alpha = 0.16f) else CardPaper)
                    .border(1.dp, if (primary) Color.Transparent else Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (primary) TextOnHero else AccentCopper,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = serif, fontSize = 19.sp, color = if (primary) TextOnHero else TextOnCreme)
            Text(body, fontSize = 13.sp, color = if (primary) TextOnHero.copy(alpha = 0.82f) else InkMuted)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (primary) TextOnHero.copy(alpha = 0.8f) else InkMuted,
        )
    }
}
