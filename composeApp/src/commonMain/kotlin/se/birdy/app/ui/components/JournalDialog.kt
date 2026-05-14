package se.birdy.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@Composable
fun JournalDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = onConfirm,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = rememberDmSerifDisplay(), color = TextOnCreme) },
        text = { Text(body, fontFamily = rememberCaveat(), color = TextOnCreme) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = AccentCopper, fontFamily = rememberCaveat())
            }
        },
        dismissButton =
            dismissLabel?.let {
                {
                    TextButton(onClick = onDismiss) {
                        Text(it, color = TextOnCreme.copy(alpha = 0.6f), fontFamily = rememberCaveat())
                    }
                }
            },
        containerColor = PaperTop,
        properties =
            if (dismissLabel == null) {
                DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            } else {
                DialogProperties()
            },
    )
}
