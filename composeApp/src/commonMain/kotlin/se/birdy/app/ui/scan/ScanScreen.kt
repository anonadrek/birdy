package se.birdy.app.ui.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ScanScreen(
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (predictionsCsv: String, frameJpegPath: String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("ScanScreen — placeholder (Task 6)")
    }
}
