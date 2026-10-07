package se.birdy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.photo_model_unavailable_body
import birdy_bird_scanner.composeapp.generated.resources.photo_model_unavailable_title
import birdy_bird_scanner.composeapp.generated.resources.photo_retry
import birdy_bird_scanner.composeapp.generated.resources.profile_back
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground

/**
 * Shown by [se.birdy.app.ui.scan.ScanScreenHost] and [se.birdy.app.ui.photoanalyze.PhotoAnalyzeHost]
 * when [se.birdy.ml.ClassifierBootstrapState.Failed] — the photo-ID model did not load.
 *
 * Albin's 2026-10-05 decision (Release 1.3.0 Plan 3 Task 2c): a failed photo model stops only
 * Scan (live camera) and photo-ID (gallery/take photo), never the rest of the app. [AppGate]
 * no longer gates on [se.birdy.ml.ClassifierBootstrapState.Failed] — Listen (audio-ID), the
 * diary, the encyclopedia, the map, Premium and settings all keep working; only the two hosts
 * that need [se.birdy.app.di.AppGraph.classifier] render this instead of their normal screen.
 * [onRetry] calls the shared bootstrap's `retry()`; both hosts observe the same
 * [se.birdy.ml.ClassifierBootstrapState] and fall back to their normal flow once it succeeds.
 */
@Composable
fun PhotoModelUnavailableView(
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().paperBackground()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            JournalHeadline(
                text = stringResource(Res.string.photo_model_unavailable_title),
                fontSize = 24.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.photo_model_unavailable_body),
                color = TextOnCreme,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentCopper,
                        contentColor = OffwhiteWarm,
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(Res.string.photo_retry))
            }
        }
        BackButton(
            onClick = onBack,
            contentDescription = stringResource(Res.string.profile_back),
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, top = 8.dp),
        )
    }
}
