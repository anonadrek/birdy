package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.profile_back
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm

/**
 * Universell tillbaka-pil. Default (papper): en 40dp ⊙-bricka med solid off-white fyllning +
 * copper-ring + copper-pil — den fyllda behållaren bryter av mot pappersbakgrunden så pilen
 * läser som en knapp i stället för att smälta in i Field Journal-ornamentiken.
 *
 * [onDark]: true på en mörk yta (t.ex. en [PhotoHero]) — byts mot den delade [GlassIconButton]
 * (mörkt glas, samma mönster som Premiums stängknapp). Default (false) är oförändrat.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    if (onDark) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier =
                modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OffwhiteWarm)
                    .border(1.5.dp, AccentCopper, CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = contentDescription,
                tint = AccentCopper,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The row a pushed paper screen's [BackButton] sits in (release 1.3.0 Task 7b): 12dp from the
 * start edge and 8dp below the top of the screen's area (the route already starts below the
 * status bar), the same place as on About, the trophy room and season statistics.
 *
 * Put it OUTSIDE the screen's scrolling content (above a LazyColumn or a verticalScroll
 * Column, or as a Scaffold topBar), so the way back stays in view however far the user has
 * scrolled. Loading, empty and error states use it too, so no state is without a way back.
 */
@Composable
fun BackTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(Res.string.profile_back),
) {
    Row(
        // 4dp at the bottom: IconButton's 48dp minimum touch size makes the button reach 4dp
        // past its 40dp box on every side, and the content scrolling below must not slide under it.
        modifier = modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBack, contentDescription = contentDescription)
    }
}

/**
 * The back button on a screen whose top is a [PhotoHero] drawn behind the status bar (the
 * species profile, Match): dark glass ([BackButton] with onDark), just under the status bar and
 * 12dp from the start edge, where the profile's button has always been.
 *
 * Call it in a Box AFTER (on top of) the screen's scrolling content, not in the hero's topBar:
 * it then stays where it is when the photo scrolls away (release 1.3.0 Task 7b). Over the paper
 * below the photo the glass disc still reads as a button.
 */
@Composable
fun BoxScope.PhotoBackButton(
    onBack: () -> Unit,
    contentDescription: String = stringResource(Res.string.profile_back),
) {
    BackButton(
        onClick = onBack,
        contentDescription = contentDescription,
        onDark = true,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = GlassBackTopPadding),
    )
}

/**
 * Top padding for a dark-glass back button under the status bar: its 36dp disc sits centred in
 * a 48dp touch target, so 2dp puts the disc's top edge 8dp down, level with the paper
 * [BackButton] in [BackTopBar] (release 1.3.0 Task 7b: the same height everywhere).
 */
val GlassBackTopPadding = 2.dp
