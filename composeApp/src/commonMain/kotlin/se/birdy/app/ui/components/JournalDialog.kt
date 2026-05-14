package se.birdy.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

@Composable
fun JournalDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton =
            if (dismissLabel != null) {
                { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
            } else {
                null
            },
        properties =
            if (dismissLabel == null) {
                DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            } else {
                DialogProperties()
            },
    )
}
