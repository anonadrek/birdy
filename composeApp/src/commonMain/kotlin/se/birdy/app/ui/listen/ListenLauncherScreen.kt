package se.birdy.app.ui.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
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
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.DailyBirdCard
import se.birdy.app.ui.components.GearButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.domain.dailybird.DailyBird

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
                    .padding(padding),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                GearButton(
                    onClick = onSettingsClick,
                    contentDescription = stringResource(Res.string.gear_content_description),
                )
            }
            JournalIntro(
                label = stringResource(Res.string.listen_journal_label),
                headline = stringResource(Res.string.listen_journal_headline),
                sub = stringResource(Res.string.listen_journal_sub),
                topPadding = 12,
            )
            val dailyBirdState by viewModel.dailyBird.collectAsState()
            dailyBirdState?.let { ui ->
                DailyBirdCard(
                    bird =
                        DailyBird(
                            speciesId = ui.speciesId,
                            seasonTag = ui.seasonTag,
                        ),
                    name = ui.name,
                    heroImagePath = ui.heroImagePath,
                    onClick = { onSpeciesProfileClick(ui.speciesId) },
                    caughtToday = ui.caughtToday,
                    matchCount = ui.matchCount,
                    huntTarget = ui.huntTarget,
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LaunchCard(
                    icon = Icons.Filled.PhotoCamera,
                    title = stringResource(Res.string.listen_card_camera_title),
                    body = stringResource(Res.string.listen_card_camera_body),
                    variant = LaunchCardVariant.Primary,
                    onClick = onCameraClick,
                )
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

private enum class LaunchCardVariant { Primary, Secondary }

@Composable
private fun LaunchCard(
    icon: ImageVector,
    title: String,
    body: String,
    variant: LaunchCardVariant,
    onClick: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    val cardBg = Color.White.copy(alpha = 0.35f)
    val borderAlpha = if (variant == LaunchCardVariant.Primary) 0.5f else 0.18f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg)
                .border(
                    width = if (variant == LaunchCardVariant.Primary) 1.5.dp else 1.dp,
                    color = AccentCopper.copy(alpha = borderAlpha),
                    shape = RoundedCornerShape(14.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(end = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        color = AccentCopper,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AccentCopper)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
            )
            Text(
                text = body,
                color = MarginaliaInk.copy(alpha = 0.78f),
                fontSize = 12.sp,
            )
        }
    }
}
