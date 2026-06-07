package se.birdy.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.map_attribution
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
            Text(
                text = stringResource(Res.string.map_attribution),
                color = MarginaliaInk,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
    }
}
