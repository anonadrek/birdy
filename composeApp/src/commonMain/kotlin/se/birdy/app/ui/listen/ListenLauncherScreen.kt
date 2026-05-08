package se.birdy.app.ui.listen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.listen_audio_locked_snackbar
import birdy_bird_scanner.composeapp.generated.resources.listen_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.listen_card_audio_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_audio_title
import birdy_bird_scanner.composeapp.generated.resources.listen_card_camera_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_camera_title
import birdy_bird_scanner.composeapp.generated.resources.listen_card_photo_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_photo_title
import birdy_bird_scanner.composeapp.generated.resources.listen_headline
import birdy_bird_scanner.composeapp.generated.resources.listen_premium_label
import birdy_bird_scanner.composeapp.generated.resources.listen_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroZone
import se.birdy.app.ui.theme.ItalicMixedText
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme

@Composable
fun ListenLauncherScreen(
    viewModel: ListenLauncherViewModel,
    onCameraClick: () -> Unit,
    onPhotoClick: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val audioLockedMsg = stringResource(Res.string.listen_audio_locked_snackbar)
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                ListenLauncherEvent.AudioLockedSnackbar -> snackbar.showSnackbar(audioLockedMsg)
            }
        }
    }
    Scaffold(
        containerColor = MossCreme,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroZone {
                Column {
                    Text(
                        text = stringResource(Res.string.listen_breadcrumb),
                        color = AccentCopperLight,
                        fontSize = 11.sp,
                        letterSpacing = 0.32.em,
                        fontWeight = FontWeight.W600,
                    )
                    Spacer(Modifier.height(8.dp))
                    ItalicMixedText(
                        text = stringResource(Res.string.listen_headline),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                color = OffwhiteWarm,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.W400,
                            ),
                        italicAccent = AccentCopperLight,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.listen_sub),
                        color = OffwhiteWarm.copy(alpha = 0.86f),
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LaunchCard(
                    icon = Icons.Filled.Hearing,
                    title = stringResource(Res.string.listen_card_audio_title),
                    body = stringResource(Res.string.listen_card_audio_body),
                    variant = LaunchCardVariant.Locked,
                    onClick = viewModel::onAudioLockedTap,
                )
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
            }
        }
    }
}

private enum class LaunchCardVariant { Locked, Primary, Secondary }

@Composable
private fun LaunchCard(
    icon: ImageVector,
    title: String,
    body: String,
    variant: LaunchCardVariant,
    onClick: () -> Unit,
) {
    val premiumLabel = stringResource(Res.string.listen_premium_label)
    val backgroundColor =
        when (variant) {
            LaunchCardVariant.Locked -> SandCreme.copy(alpha = 0.6f)
            LaunchCardVariant.Primary -> SandCreme
            LaunchCardVariant.Secondary -> SandCreme
        }
    val borderColor =
        when (variant) {
            LaunchCardVariant.Primary -> AccentCopper
            else -> AccentCopper.copy(alpha = 0.0f)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
                .border(
                    width = if (variant == LaunchCardVariant.Primary) 2.dp else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(20.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(end = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentCopper.copy(alpha = if (variant == LaunchCardVariant.Locked) 0.10f else 0.18f))
                    .padding(10.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AccentCopperLight)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = TextOnCreme,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.W700,
                    fontSize = 18.sp,
                )
                if (variant == LaunchCardVariant.Locked) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = AccentCopperLight,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = premiumLabel,
                        color = AccentCopperLight,
                        fontSize = 9.sp,
                        letterSpacing = 0.18.em,
                        fontWeight = FontWeight.W600,
                    )
                }
            }
            Text(
                text = body,
                color = TextOnCreme.copy(alpha = 0.74f),
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
        }
        if (variant != LaunchCardVariant.Locked) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AccentCopper,
            )
        }
    }
}
