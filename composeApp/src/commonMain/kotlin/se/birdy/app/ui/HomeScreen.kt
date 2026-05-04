package se.birdy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.content.Locale

@Composable
fun HomeScreen() {
    val repo = remember { SpeciesRepositoryProvider.get() }
    val state =
        remember { repo.all(Locale.SV) }.collectAsState(initial = emptyList())

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Birdy Bird Scanner",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "${state.value.size} fågelarter laddade",
            style = MaterialTheme.typography.titleMedium,
        )
        for (s in state.value.take(5)) {
            Text(s.name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
