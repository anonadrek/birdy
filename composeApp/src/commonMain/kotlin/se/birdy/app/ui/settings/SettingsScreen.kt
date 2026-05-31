package se.birdy.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.settings_back
import birdy_bird_scanner.composeapp.generated.resources.settings_dev_trigger_recap
import birdy_bird_scanner.composeapp.generated.resources.settings_feedback_subject
import birdy_bird_scanner.composeapp.generated.resources.settings_footer
import birdy_bird_scanner.composeapp.generated.resources.settings_hero_accent
import birdy_bird_scanner.composeapp.generated.resources.settings_hero_plain
import birdy_bird_scanner.composeapp.generated.resources.settings_hero_subline
import birdy_bird_scanner.composeapp.generated.resources.settings_label_about
import birdy_bird_scanner.composeapp.generated.resources.settings_label_language
import birdy_bird_scanner.composeapp.generated.resources.settings_label_name
import birdy_bird_scanner.composeapp.generated.resources.settings_language_en
import birdy_bird_scanner.composeapp.generated.resources.settings_language_sv
import birdy_bird_scanner.composeapp.generated.resources.settings_language_system
import birdy_bird_scanner.composeapp.generated.resources.settings_name_dialog_cancel
import birdy_bird_scanner.composeapp.generated.resources.settings_name_dialog_save
import birdy_bird_scanner.composeapp.generated.resources.settings_name_dialog_title
import birdy_bird_scanner.composeapp.generated.resources.settings_notifications_disabled_helpline
import birdy_bird_scanner.composeapp.generated.resources.settings_restore_purchases
import birdy_bird_scanner.composeapp.generated.resources.settings_row_feedback
import birdy_bird_scanner.composeapp.generated.resources.settings_row_privacy
import birdy_bird_scanner.composeapp.generated.resources.settings_row_rate
import birdy_bird_scanner.composeapp.generated.resources.settings_row_share
import birdy_bird_scanner.composeapp.generated.resources.settings_row_terms
import birdy_bird_scanner.composeapp.generated.resources.settings_row_website
import birdy_bird_scanner.composeapp.generated.resources.settings_section_about_birdy
import birdy_bird_scanner.composeapp.generated.resources.settings_section_account
import birdy_bird_scanner.composeapp.generated.resources.settings_section_legal
import birdy_bird_scanner.composeapp.generated.resources.settings_section_notifications
import birdy_bird_scanner.composeapp.generated.resources.settings_share_copy
import birdy_bird_scanner.composeapp.generated.resources.settings_show_intro_again
import birdy_bird_scanner.composeapp.generated.resources.settings_title
import birdy_bird_scanner.composeapp.generated.resources.settings_toggle_daily_bird
import birdy_bird_scanner.composeapp.generated.resources.settings_toggle_weekly_recap
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.components.OrnamentRule
import se.birdy.app.ui.components.PremiumHeroCard
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.datastore.AppLanguage

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPremiumClick: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onShowIntroAgain: () -> Unit,
    versionName: String,
) {
    val state by viewModel.state.collectAsState()
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val shareText = stringResource(Res.string.settings_share_copy)
    val feedbackSubject = stringResource(Res.string.settings_feedback_subject, versionName)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.RestartForLocale -> applyLocale(effect.tag)
                is SettingsEffect.ShowToast -> {
                    val text = getString(effect.text)
                    snackbarHostState.showSnackbar(text)
                }
                SettingsEffect.OpenPrivacyUrl -> openExternalUrl("https://birdy.community/legal/privacy/")
                SettingsEffect.OpenTermsUrl -> openExternalUrl("https://birdy.community/legal/terms/")
                SettingsEffect.OpenWebsiteUrl -> openExternalUrl("https://birdy.community/")
                SettingsEffect.RateOnPlayStore -> openPlayStoreListing("se.birdy.android")
                SettingsEffect.ShareApp -> shareApp(shareText)
                SettingsEffect.SendFeedback -> openMailto("albin@abrahamssons.se", feedbackSubject)
                SettingsEffect.OpenAbout -> onNavigateToAbout()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().paperBackground()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { TopBar(onBack = onBack) }
            if (!state.premiumActive) {
                item {
                    PremiumHeroCard(
                        headlinePlain = stringResource(Res.string.settings_hero_plain),
                        headlineAccent = stringResource(Res.string.settings_hero_accent),
                        subline = stringResource(Res.string.settings_hero_subline),
                        onClick = onPremiumClick,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item { OrnamentRule() }
            }
            item { SectionHeader(stringResource(Res.string.settings_section_account)) }
            item {
                PaperCard {
                    SettingsRow(
                        icon = Icons.Outlined.Person,
                        label = stringResource(Res.string.settings_label_name),
                        value = state.userName.ifEmpty { "—" },
                        onClick = { showNameDialog = true },
                    )
                    DashedDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Language,
                        label = stringResource(Res.string.settings_label_language),
                        value = stringResource(state.language.labelRes()),
                        onClick = { showLanguageDialog = true },
                    )
                }
            }
            item { SectionHeader(stringResource(Res.string.settings_section_notifications)) }
            item {
                val dailyBirdEnabled by viewModel.dailyBirdPushEnabled.collectAsState()
                val weeklyRecapEnabled by viewModel.weeklyRecapPushEnabled.collectAsState()
                val systemNotifEnabled = viewModel.areNotificationsEnabled()

                PaperCard {
                    ToggleRow(
                        icon = Icons.Outlined.Notifications,
                        label = stringResource(Res.string.settings_toggle_daily_bird),
                        checked = dailyBirdEnabled,
                        onCheckedChange = viewModel::setDailyBirdPushEnabled,
                    )
                    DashedDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Notifications,
                        label = stringResource(Res.string.settings_toggle_weekly_recap),
                        checked = weeklyRecapEnabled,
                        onCheckedChange = viewModel::setWeeklyRecapPushEnabled,
                    )
                    if (!systemNotifEnabled) {
                        DashedDivider()
                        SettingsRow(
                            icon = Icons.Outlined.Notifications,
                            label = stringResource(Res.string.settings_notifications_disabled_helpline),
                            value = null,
                            onClick = { viewModel.openAppNotificationSettings() },
                        )
                    }
                }
            }
            if (viewModel.devToolsAvailable) {
                item { SectionHeader("DEV TOOLS") }
                item {
                    PaperCard {
                        SettingsRow(
                            icon = Icons.Outlined.Notifications,
                            label = "DEV: Trigger Daily Bird push",
                            value = null,
                            onClick = { viewModel.devTriggerDailyBirdPush() },
                        )
                        DashedDivider()
                        SettingsRow(
                            icon = Icons.Outlined.Notifications,
                            label = stringResource(Res.string.settings_dev_trigger_recap),
                            value = null,
                            onClick = { viewModel.devTriggerWeeklyRecapPush() },
                        )
                    }
                }
            }
            item { SectionHeader(stringResource(Res.string.settings_section_about_birdy)) }
            item {
                PaperCard {
                    SettingsRow(
                        icon = Icons.Outlined.Public,
                        label = stringResource(Res.string.settings_row_website),
                        value = null,
                        onClick = { viewModel.openWebsite() },
                    )
                    DashedDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Star,
                        label = stringResource(Res.string.settings_row_rate),
                        value = null,
                        onClick = { viewModel.rateOnPlayStore() },
                    )
                    DashedDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Share,
                        label = stringResource(Res.string.settings_row_share),
                        value = null,
                        onClick = { viewModel.shareApp() },
                    )
                    DashedDivider()
                    SettingsRow(
                        icon = Icons.Outlined.MailOutline,
                        label = stringResource(Res.string.settings_row_feedback),
                        value = null,
                        onClick = { viewModel.sendFeedback() },
                    )
                    DashedDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        label = stringResource(Res.string.settings_label_about),
                        value = "v$versionName",
                        onClick = { viewModel.openAbout() },
                    )
                    DashedDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Replay,
                        label = stringResource(Res.string.settings_show_intro_again),
                        value = null,
                        onClick = onShowIntroAgain,
                    )
                    DashedDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Refresh,
                        label = stringResource(Res.string.settings_restore_purchases),
                        value = null,
                        onClick = { viewModel.restorePurchases() },
                    )
                }
            }
            item { SectionHeader(stringResource(Res.string.settings_section_legal)) }
            item {
                PaperCard {
                    SettingsRow(
                        icon = Icons.Outlined.VerifiedUser,
                        label = stringResource(Res.string.settings_row_privacy),
                        value = null,
                        onClick = { viewModel.openPrivacy() },
                    )
                    DashedDivider()
                    SettingsRow(
                        icon = Icons.Outlined.VerifiedUser,
                        label = stringResource(Res.string.settings_row_terms),
                        value = null,
                        onClick = { viewModel.openTerms() },
                    )
                }
            }
            item {
                Text(
                    text = stringResource(Res.string.settings_footer),
                    fontFamily = rememberCaveat(),
                    fontSize = 14.sp,
                    color = MarginaliaInk,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
        )
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
    if (showLanguageDialog) {
        LanguagePickerDialog(
            current = state.language,
            onSelect = {
                viewModel.saveLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

private fun AppLanguage.labelRes(): StringResource =
    when (this) {
        AppLanguage.SV -> Res.string.settings_language_sv
        AppLanguage.EN -> Res.string.settings_language_en
        AppLanguage.SYSTEM -> Res.string.settings_language_system
    }

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(
            onClick = onBack,
            contentDescription = stringResource(Res.string.settings_back),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(Res.string.settings_title),
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
            fontSize = 22.sp,
            color = TextOnCreme,
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.16.em,
        color = MarginaliaInk,
        modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun PaperCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SandCreme),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(1.5.dp, AccentCopper, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AccentCopper, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = label,
            color = TextOnCreme,
            fontSize = 14.sp,
            fontWeight = FontWeight.W500,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                color = MarginaliaInk,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text("›", color = AccentCopper, fontSize = 18.sp, fontWeight = FontWeight.W600)
    }
}

@Composable
private fun DashedDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(MarginaliaInk.copy(alpha = 0.18f)),
    )
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(1.5.dp, AccentCopper, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AccentCopper, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = label,
            color = TextOnCreme,
            fontSize = 14.sp,
            fontWeight = FontWeight.W500,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentCopper,
                    uncheckedThumbColor = MarginaliaInk.copy(alpha = 0.6f),
                    uncheckedTrackColor = MarginaliaInk.copy(alpha = 0.18f),
                    uncheckedBorderColor = MarginaliaInk.copy(alpha = 0.3f),
                ),
        )
    }
}

@Composable
private fun NameEditDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    val canSave = draft.trim().isNotEmpty()
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
            TextButton(onClick = { onSave(draft) }, enabled = canSave) {
                Text(stringResource(Res.string.settings_name_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_name_dialog_cancel))
            }
        },
        containerColor = PaperTop,
    )
}

@Composable
private fun LanguagePickerDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val options =
        remember {
            listOf(
                AppLanguage.SV to Res.string.settings_language_sv,
                AppLanguage.EN to Res.string.settings_language_en,
                AppLanguage.SYSTEM to Res.string.settings_language_system,
            )
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_label_language)) },
        text = {
            Column {
                options.forEach { (lang, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onSelect(lang) })
                                .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = current == lang, onClick = { onSelect(lang) })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(label), color = TextOnCreme, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_name_dialog_cancel))
            }
        },
        containerColor = PaperTop,
    )
}
