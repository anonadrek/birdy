package se.birdy.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.settings_back
import birdy_bird_scanner.composeapp.generated.resources.settings_label_about
import birdy_bird_scanner.composeapp.generated.resources.settings_label_language
import birdy_bird_scanner.composeapp.generated.resources.settings_label_name
import birdy_bird_scanner.composeapp.generated.resources.settings_language_en
import birdy_bird_scanner.composeapp.generated.resources.settings_language_sv
import birdy_bird_scanner.composeapp.generated.resources.settings_language_system
import birdy_bird_scanner.composeapp.generated.resources.settings_name_dialog_cancel
import birdy_bird_scanner.composeapp.generated.resources.settings_name_dialog_save
import birdy_bird_scanner.composeapp.generated.resources.settings_name_dialog_title
import birdy_bird_scanner.composeapp.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.datastore.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MossCreme,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title), fontWeight = FontWeight.W700) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.settings_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MossCreme),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsRow(
                label = stringResource(Res.string.settings_label_name),
                value = state.userName.ifEmpty { "—" },
                onClick = { showNameDialog = true },
            )
            LanguageSection(
                current = state.language,
                onSelect = { viewModel.saveLanguage(it) },
            )
            SettingsRow(
                label = stringResource(Res.string.settings_label_about),
                value = "v0.7.0a", // bumpa när tag sätts
                onClick = { /* about-dialog kommer i Plan 7b — visa version i 7a */ },
            )
        }
    }

    if (showNameDialog) {
        NameEditDialog(
            initial = state.userName,
            onSave = {
                viewModel.saveName(it)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false },
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SandCreme)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextOnCreme, fontWeight = FontWeight.W600, fontSize = 16.sp)
        Text(value, color = AccentCopper, fontWeight = FontWeight.W600, fontSize = 16.sp)
    }
}

@Composable
private fun LanguageSection(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SandCreme)
                .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_label_language),
            color = TextOnCreme,
            fontWeight = FontWeight.W600,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            AppLanguage.SV to Res.string.settings_language_sv,
            AppLanguage.EN to Res.string.settings_language_en,
            AppLanguage.SYSTEM to Res.string.settings_language_system,
        ).forEach { (lang, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onSelect(lang) })
                        .padding(vertical = 6.dp),
            ) {
                RadioButton(selected = current == lang, onClick = { onSelect(lang) })
                Spacer(Modifier.height(0.dp))
                Text(stringResource(label), color = TextOnCreme, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun NameEditDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_name_dialog_title)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text(stringResource(Res.string.settings_name_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_name_dialog_cancel))
            }
        },
        containerColor = MossCreme,
    )
}
