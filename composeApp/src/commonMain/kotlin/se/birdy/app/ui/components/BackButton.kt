package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
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
 *
 * [enabled]: false while leaving would lose work (a save in progress, release 1.3.0 Task 7b):
 * dimmed like the other disabled buttons and announced as disabled. The screen then also
 * swallows the system back gesture, so the arrow and the gesture still do the same thing.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    enabled: Boolean = true,
) {
    if (onDark) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
    } else {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier =
                modifier
                    .alpha(if (enabled) 1f else DISABLED_ALPHA)
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
 * status bar), the same place as on About, the trophy room and season statistics. [content]
 * follows the button in the row (Settings puts its title there).
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
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit = {},
) {
    Row(
        // 4dp at the bottom: IconButton's 48dp minimum touch size draws the paper disc 4dp past
        // its 40dp box on every side, and the content scrolling below must not slide over it.
        modifier = modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBack, contentDescription = contentDescription, enabled = enabled)
        content()
    }
}

/**
 * The back button on a screen whose top is a [PhotoHero] drawn behind the status bar (the
 * species profile, Match): dark glass ([BackButton] with onDark), just under the status bar and
 * 12dp from the start edge, its disc level with the paper button's (see [backButtonPlacement]).
 *
 * Call it in a Box AFTER (on top of) the screen's scrolling content, not in the hero's topBar:
 * it then stays where it is when the photo scrolls away (release 1.3.0 Task 7b). Over the paper
 * below the photo the glass disc still reads as a button.
 */
@Composable
fun BoxScope.PhotoBackButton(
    onBack: () -> Unit,
    contentDescription: String = stringResource(Res.string.profile_back),
    enabled: Boolean = true,
) {
    BackButton(
        onClick = onBack,
        contentDescription = contentDescription,
        onDark = true,
        enabled = enabled,
        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().backButtonPlacement(onDark = true),
    )
}

/**
 * Where a [BackButton] goes at the top start of a screen's area, so the disc's top edge is at the
 * same height everywhere (release 1.3.0 Task 7b):
 * - paper ([BackTopBar], About, the trophy room, season statistics): 12dp in, 8dp down. The
 *   paper disc is drawn 48dp wide around its 40dp box (IconButton's minimum touch size), so its
 *   top edge is 4dp below the top of the area.
 * - dark glass: its 36dp disc sits centred in its 48dp touch target, 6dp in from the target's
 *   edge, so the button is moved up 2dp ([GlassBackTopOffset]) to put the disc's top edge 4dp
 *   below the top as well.
 * BackButtonLevelTest checks the two discs' top edges against each other.
 */
fun Modifier.backButtonPlacement(onDark: Boolean): Modifier =
    if (onDark) {
        padding(start = 12.dp).offset(y = GlassBackTopOffset)
    } else {
        padding(start = 12.dp, top = 8.dp)
    }

/** See [backButtonPlacement]: 4dp (paper disc's top) − 6dp (glass disc's inset in its touch target). */
val GlassBackTopOffset = -2.dp
