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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_hero_a11y
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_kicker_fmt
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_read_more
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
import se.birdy.app.dailybird.DailyBirdToday
import se.birdy.app.dailybird.challenge
import se.birdy.app.ui.components.GearButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.PhotoHero
import se.birdy.app.ui.components.hairlineBottom
import se.birdy.app.ui.dailybird.DailyBirdHeroActions
import se.birdy.app.ui.dailybird.DailyBirdHeroChallengeRow
import se.birdy.app.ui.dailybird.dailyBirdDateA11yLabel
import se.birdy.app.ui.dailybird.dailyBirdDateLabel
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperDeep
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri

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

    Box(Modifier.fillMaxSize().paperBackground()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            val dailyBirdState by viewModel.dailyBird.collectAsState()
            val dailyBirdBadgeUnlocked by viewModel.dailyBirdBadgeUnlocked.collectAsState()
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
            val bird = dailyBirdState
            if (bird != null) {
                DailyBirdHero(
                    bird = bird,
                    onReadMore = { onSpeciesProfileClick(bird.speciesId) },
                    onListen = viewModel::onAudioCardTap,
                    topBar = gearOnHero,
                    // Dagens fågel-jägare is a Premium badge: a label on the row, nothing is gated.
                    showPremiumBadgeTag = !dailyBirdBadgeUnlocked,
                )
            } else {
                Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), content = gearOnPaper)
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
 * Today's bird as the screen's [PhotoHero] (release 1.3.0 Task 7d, design option B): the date in
 * the kicker, the species name, the scientific name where the season line used to be ("Här just
 * nu" read the same nearly every day), two actions and the challenge row toward Dagens
 * fågel-jägare. Everything new sits in PhotoHero's own bottomContent slot, on the hero's existing
 * text area: no overlay of its own.
 *
 * The whole photo still opens the profile, as before; the hero is one merged TalkBack node (date,
 * names and the challenge sentence), while the two buttons and [topBar]'s gear stay separately
 * focusable because clickable() makes each its own merge boundary.
 */
@Composable
private fun DailyBirdHero(
    bird: DailyBirdToday,
    onReadMore: () -> Unit,
    onListen: () -> Unit,
    topBar: @Composable BoxScope.() -> Unit,
    showPremiumBadgeTag: Boolean,
) {
    val date = dailyBirdDateLabel(bird.date)
    val spokenDate = dailyBirdDateA11yLabel(bird.date)
    val a11y = stringResource(Res.string.daily_bird_hero_a11y, spokenDate, bird.name, bird.scientificName)
    // PhotoHero grows to fit its text block, but the block doesn't keep clear of the gear row: with
    // the buttons and the challenge row at large font scales it reached the top and drew the kicker
    // under the gear (identify_sv_200). The minimum height grows with the font scale instead, which
    // leaves the gear row free up to 2.0 even with a two-line name, scientific name and buttons.
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    PhotoHero(
        kicker = stringResource(Res.string.daily_bird_kicker_fmt, date),
        title = bird.name,
        subtitle = bird.scientificName,
        height = HeroMinHeight + HeroGrowthPerFontScale * (fontScale - 1f),
        drawBehindStatusBar = true,
        modifier =
            Modifier
                .clickable(onClickLabel = stringResource(Res.string.daily_bird_read_more), onClick = onReadMore)
                .semantics(mergeDescendants = true) { contentDescription = a11y },
        image =
            bird.heroImagePath?.let { path ->
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
        bottomContent = {
            DailyBirdHeroActions(
                onReadMore = onReadMore,
                onListen = onListen,
                modifier = Modifier.padding(top = 6.dp),
            )
            DailyBirdHeroChallengeRow(
                challenge = bird.challenge(),
                modifier = Modifier.padding(top = 6.dp),
                showPremiumTag = showPremiumBadgeTag,
            )
        },
    )
}

private val HeroMinHeight = 380.dp
private val HeroGrowthPerFontScale = 230.dp

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
                        m.hairlineBottom()
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
