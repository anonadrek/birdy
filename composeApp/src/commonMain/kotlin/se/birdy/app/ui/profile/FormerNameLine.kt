package se.birdy.app.ui.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.profile_former_name
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.LATIN_NAME_TEXT_ALPHA
import se.birdy.app.ui.theme.TextOnHero

/**
 * "Tidigare: Sädgås" under a renamed species' name on the profile (release 1.3.0 Task 7m: Birdy
 * took BirdLife Sverige's official Swedish names, so Sädgås is now Skogsgås). Shown only when the
 * species has a former name in the app's language; English names were not renamed, so English
 * users never see it. Same color as the scientific name above it, which PhotoHeroContrastTest
 * holds to WCAG AA on the photo band and the text scrim.
 */
@Composable
internal fun FormerNameLine(
    formerName: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(Res.string.profile_former_name, formerName),
        color = TextOnHero.copy(alpha = LATIN_NAME_TEXT_ALPHA),
        fontSize = 13.sp,
        lineHeight = 16.sp,
        modifier = modifier.padding(top = 4.dp),
    )
}
