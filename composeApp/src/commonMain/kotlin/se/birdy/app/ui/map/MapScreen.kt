package se.birdy.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.map_attribution_maptiler
import birdy_bird_scanner.composeapp.generated.resources.map_attribution_osm
import birdy_bird_scanner.composeapp.generated.resources.map_empty
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground

/** Android draws the osmdroid surface. composeApp is Android-only, so one actual suffices. */
@Composable
expect fun MapScreenHost(
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    modifier: Modifier,
)

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onPinClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(modifier = modifier.fillMaxSize().paperBackground()) {
        if (state.pins.isEmpty()) {
            Text(
                text = stringResource(Res.string.map_empty),
                color = MarginaliaInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        } else {
            MapScreenHost(pins = state.pins, onPinClick = onPinClick, modifier = Modifier.fillMaxSize())
            // MapTiler + OSM require visible attribution with clickable links to both copyright
            // pages; MapTiler is credited first. See www.maptiler.com/copyright + osmfoundation.org.
            val linkStyles =
                TextLinkStyles(style = SpanStyle(color = MarginaliaInk, textDecoration = TextDecoration.Underline))
            val attribution =
                buildAnnotatedString {
                    withLink(LinkAnnotation.Url("https://www.maptiler.com/copyright/", linkStyles)) {
                        append(stringResource(Res.string.map_attribution_maptiler))
                    }
                    append(" · ")
                    withLink(LinkAnnotation.Url("https://www.openstreetmap.org/copyright", linkStyles)) {
                        append(stringResource(Res.string.map_attribution_osm))
                    }
                }
            Text(
                text = attribution,
                color = MarginaliaInk,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
    }
}
