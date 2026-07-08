package se.birdy.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import se.birdy.app.ui.components.IosComingSoonPanel

@Composable
actual fun MapScreenHost(
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    modifier: Modifier,
) = IosComingSoonPanel()
