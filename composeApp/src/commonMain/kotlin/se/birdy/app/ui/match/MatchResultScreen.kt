package se.birdy.app.ui.match

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.result_no_matches
import birdy_bird_scanner.composeapp.generated.resources.result_no_predictions
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.content.Locale

@Composable
fun MatchResultScreen(
    viewModel: MatchResultViewModel,
    onBack: () -> Unit,
    locale: Locale,
    zone: TimeZone,
) {
    val state by viewModel.state.collectAsState()
    Box(modifier = Modifier.fillMaxSize().paperBackground()) {
        when (val s = state) {
            MatchResultUiState.Loading -> JournalLoading()
            is MatchResultUiState.Error -> {
                val msg =
                    when (s.kind) {
                        MatchResultUiState.Error.Kind.NoPredictions ->
                            stringResource(Res.string.result_no_predictions)
                        MatchResultUiState.Error.Kind.ParseFailed ->
                            stringResource(Res.string.result_no_matches)
                    }
                Text(msg, modifier = Modifier.align(Alignment.Center), color = TextOnCreme)
            }
            is MatchResultUiState.NoBird ->
                NoBirdView(
                    state = s,
                    onRetry = onBack,
                    zone = zone,
                )
            is MatchResultUiState.Disambig ->
                DisambigView(
                    state = s,
                    onPick = { speciesId -> viewModel.pickFromDisambig(speciesId) },
                    onSaveAsUnknown = { viewModel.saveAsUnknown() },
                    onUnknownSaved = onBack,
                    onCancel = onBack,
                )
            is MatchResultUiState.Match ->
                MatchView(
                    state = s,
                    onSave = { note -> viewModel.saveToDiary(note) },
                    onCancel = onBack,
                    onDismissUnlock = { viewModel.dismissUnlock() },
                    locale = locale,
                    zone = zone,
                )
        }
    }
}
