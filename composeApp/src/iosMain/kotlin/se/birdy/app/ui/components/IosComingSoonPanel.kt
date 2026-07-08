package se.birdy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.ios_coming_soon_body
import birdy_bird_scanner.composeapp.generated.resources.ios_coming_soon_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun IosComingSoonPanel(onBack: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(Res.string.ios_coming_soon_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.ios_coming_soon_body), textAlign = TextAlign.Center)
        onBack?.let { Button(onClick = it) { Text("←") } }
    }
}
