package se.birdy.app.ui.diary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.birdy.app.ui.theme.MossCreme

@Suppress("UNUSED_PARAMETER")
@Composable
fun LifelistScreen(
    viewModel: LifelistViewModel,
    onObservationClick: (id: String) -> Unit,
    onScanCtaClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = MossCreme) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val label =
                when (val s = state) {
                    LifelistUiState.Loading -> "Loading"
                    LifelistUiState.Empty -> "Empty"
                    is LifelistUiState.Loaded ->
                        "${s.userName} · ${s.speciesCount} species · ${s.stampsCount} stamps"
                }
            Text(label, modifier = Modifier.align(Alignment.Center))
        }
    }
}
