package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_all
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_auks
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_cranes_rails
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_doves
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_gamebirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_grebes_divers
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_gulls_terns
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_herons_storks
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_other
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_owls
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_raptors
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_seabirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_songbirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_waders
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_waterfowl
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_woodpeckers
import birdy_bird_scanner.composeapp.generated.resources.archive_empty_group_body
import birdy_bird_scanner.composeapp.generated.resources.archive_empty_group_title
import birdy_bird_scanner.composeapp.generated.resources.archive_error_body
import birdy_bird_scanner.composeapp.generated.resources.archive_error_retry
import birdy_bird_scanner.composeapp.generated.resources.archive_error_title
import birdy_bird_scanner.composeapp.generated.resources.archive_extinct_tag
import birdy_bird_scanner.composeapp.generated.resources.archive_family_header_description
import birdy_bird_scanner.composeapp.generated.resources.archive_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.archive_journal_label
import birdy_bird_scanner.composeapp.generated.resources.archive_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.archive_red_listed_tag
import birdy_bird_scanner.composeapp.generated.resources.archive_search_clear
import birdy_bird_scanner.composeapp.generated.resources.archive_section_count
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_alpha
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_family
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_recent
import birdy_bird_scanner.composeapp.generated.resources.menu_button
import birdy_bird_scanner.composeapp.generated.resources.premium_archive_subtitle
import birdy_bird_scanner.composeapp.generated.resources.premium_archive_title
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_corner
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_cta
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_export_active
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_export_busy
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_export_empty
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_export_failed
import birdy_bird_scanner.composeapp.generated.resources.search_empty_body
import birdy_bird_scanner.composeapp.generated.resources.search_empty_title
import birdy_bird_scanner.composeapp.generated.resources.search_placeholder
import birdy_bird_scanner.composeapp.generated.resources.settings_menu_item
import birdy_bird_scanner.composeapp.generated.resources.sort_chip_description
import birdy_bird_scanner.composeapp.generated.resources.species_photo_label
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BirdyPill
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.MiniStamp
import se.birdy.app.ui.components.PremiumTeaserCard
import se.birdy.app.ui.components.hairlineBottom
import se.birdy.app.ui.settings.shareJournalPdf
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.OutlineInk
import se.birdy.app.ui.theme.RedListTagBg
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.StampNavy
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.usecase.JournalExportResult
import se.birdy.app.util.isExtinct
import se.birdy.app.util.isRedListed
import se.birdy.app.util.speciesImageUri
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.datastore.ArchiveSort

// Pre-existing debt (LongParameterList/LongMethod/CyclomaticComplexMethod in detekt-baseline.xml): detekt keys
// baseline entries by the signature text, so Task 7d's two daily-bird parameters re-key them without
// changing the function's real size. Suppressed here instead of growing the baseline (AppScaffold precedent).
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    locale: Locale,
    onSpeciesClick: (SpeciesId) -> Unit,
    onPremiumClick: () -> Unit,
    onJournalExport: (suspend () -> JournalExportResult)? = null,
    onSharePdf: (String) -> Unit = ::shareJournalPdf,
    showPremiumTeaser: Boolean = true,
    showDebugMenu: Boolean = false,
    onDebugBenchmarkClick: () -> Unit = {},
    showDebugDiagnostics: Boolean = false,
    onDebugDiagnosticsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    dailyBird: se.birdy.app.dailybird.DailyBirdToday? = null,
    onDailyBirdClick: (speciesId: String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val chip by viewModel.chip.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val premiumActive by viewModel.premiumActive.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportActiveLabel = stringResource(Res.string.premium_teaser_export_active)
    val exportBusyLabel = stringResource(Res.string.premium_teaser_export_busy)
    val exportEmptyMsg = stringResource(Res.string.premium_teaser_export_empty)
    val exportFailedMsg = stringResource(Res.string.premium_teaser_export_failed)

    JournalScaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        // T10b Important 2: no more global spacedBy — a row's own hairlineBottom + vertical
        // padding already gives it symmetric breathing room on both sides, so an outer spacedBy
        // on top of that made row-to-row gaps uneven (10dp below a row's content, 18dp above the
        // next). Each NON-row item below (header/search/chips/sort/premium/count) instead carries
        // its own trailing `padding(bottom = 8.dp)`, replicating the old spacedBy(8.dp) look
        // between them; rows and family headers sit flush against each other and against
        // whatever preceded them.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "header") {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    JournalIntro(
                        label = stringResource(Res.string.archive_journal_label),
                        headline = stringResource(Res.string.archive_journal_headline),
                        sub = stringResource(Res.string.archive_journal_sub),
                        headlineFontSize = 36.sp,
                    )
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 16.dp)) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(Res.string.menu_button),
                                tint = AccentCopper,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = CardPaper,
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.settings_menu_item)) },
                                onClick = {
                                    onSettingsClick()
                                    menuExpanded = false
                                },
                            )
                            if (showDebugMenu) {
                                // Debug-only menu items, intentionally English (not user-facing).
                                DropdownMenuItem(
                                    text = { Text("Run benchmark") },
                                    onClick = {
                                        onDebugBenchmarkClick()
                                        menuExpanded = false
                                    },
                                )
                                if (showDebugDiagnostics) {
                                    DropdownMenuItem(
                                        text = { Text("ML diagnos") },
                                        onClick = {
                                            onDebugDiagnosticsClick()
                                            menuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Release 1.3.0 Task 7d (design option B): the Dagens fågel strip, above the search field.
            if (dailyBird != null) {
                item(key = "daily-bird") {
                    se.birdy.app.ui.dailybird.DailyBirdStrip(
                        bird = dailyBird,
                        onClick = { onDailyBirdClick(dailyBird.speciesId) },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }

            // Spec gap B (2026-09-24 §3 item 6): search field sits right below the header, then
            // group pills, then sorting — matching the approved spec's reading order. The premium
            // teaser moved below sorting, before the results (was between header and search).
            item(key = "search") {
                JournalSearchField(
                    value = query,
                    onValueChange = viewModel::onQueryChanged,
                    onClear = viewModel::clearQuery,
                    placeholder = stringResource(Res.string.search_placeholder),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                )
            }

            item(key = "chips") {
                ChipBar(
                    selected = chip,
                    onSelect = viewModel::onChipSelected,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            item(key = "sort") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SortChip(sort = sort, onClick = viewModel::onSortToggle)
                }
            }

            // Plan 6b3 T21 fix: render teaser-card when EITHER we want to upsell
            // (!premium → unlock CTA) OR premium is active AND we have an export
            // lambda (→ "Export Field Journal" CTA). Previously the card was only
            // shown to non-premium users, hiding the export flow entirely after T8.
            if (showPremiumTeaser || (premiumActive && onJournalExport != null)) {
                item(key = "premium") {
                    PremiumTeaserCard(
                        title = stringResource(Res.string.premium_archive_title),
                        subtitle = stringResource(Res.string.premium_archive_subtitle),
                        cornerLabel = stringResource(Res.string.premium_teaser_corner),
                        ctaLabel = stringResource(Res.string.premium_teaser_cta),
                        onUnlock = onPremiumClick,
                        premiumActive = premiumActive,
                        isExporting = isExporting,
                        exportLabel = onJournalExport?.let { exportActiveLabel },
                        exportBusyLabel = exportBusyLabel,
                        onExport = {
                            val exportFn = onJournalExport ?: return@PremiumTeaserCard
                            if (isExporting) return@PremiumTeaserCard
                            isExporting = true
                            scope.launch {
                                val result =
                                    runCatching { exportFn() }
                                        .onFailure { if (it is CancellationException) throw it }
                                        .getOrElse {
                                            JournalExportResult.Failed(it.message ?: "Unknown error")
                                        }
                                isExporting = false
                                when (result) {
                                    is JournalExportResult.Success -> onSharePdf(result.pdfPath)
                                    JournalExportResult.NothingToExport ->
                                        snackbarHostState.showSnackbar(exportEmptyMsg)
                                    is JournalExportResult.Failed ->
                                        snackbarHostState.showSnackbar(exportFailedMsg)
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                    )
                }
            }

            when (val s = state) {
                ArchiveUiState.Loading ->
                    items(8, key = { "skeleton-$it" }) {
                        SpeciesRowSkeleton()
                    }
                ArchiveUiState.Empty ->
                    item(key = "empty") {
                        // A blank query with no rows means a group chip filtered everything out —
                        // the "search by scientific name" hint would be nonsensical there.
                        if (query.isBlank()) {
                            EmptyState(
                                title = stringResource(Res.string.archive_empty_group_title),
                                body = stringResource(Res.string.archive_empty_group_body),
                            )
                        } else {
                            EmptyState(
                                title = stringResource(Res.string.search_empty_title),
                                body = stringResource(Res.string.search_empty_body),
                            )
                        }
                    }
                is ArchiveUiState.Error ->
                    item(key = "error") {
                        ArchiveErrorBlock(onRetry = viewModel::retry)
                    }
                is ArchiveUiState.Loaded -> {
                    item(key = "count") {
                        Text(
                            // T10b Important 3: restyled to match FamilyHeader (was 9sp W700
                            // MarginaliaInk@0.6, ~3:1 — failed AA) and pluralized ("1 arter" was
                            // grammatically wrong for a single result).
                            text =
                                pluralStringResource(Res.plurals.archive_section_count, s.rows.size, s.rows.size)
                                    .uppercase(),
                            color = InkMuted,
                            fontWeight = FontWeight.W600,
                            fontSize = 9.5.sp,
                            lineHeight = 12.sp,
                            letterSpacing = 0.15.em,
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .padding(bottom = 8.dp),
                        )
                    }
                    if (s.sort == ArchiveSort.FAMILY) {
                        val grouped = s.rows.groupBy { it.summary.family }
                        grouped.forEach { (family, rows) ->
                            stickyHeader(key = "family-$family") {
                                FamilyHeader(
                                    family = family,
                                    familySv = rows.first().summary.familySv,
                                    locale = locale,
                                    count = rows.size,
                                )
                            }
                            items(rows, key = { it.summary.id.raw }) { row ->
                                SpeciesRow(
                                    summary = row.summary,
                                    isStamped = row.isStamped,
                                    stampNumber = row.stampNumber,
                                    onClick = { onSpeciesClick(row.summary.id) },
                                )
                            }
                        }
                    } else {
                        items(s.rows, key = { it.summary.id.raw }) { row ->
                            SpeciesRow(
                                summary = row.summary,
                                isStamped = row.isStamped,
                                stampNumber = row.stampNumber,
                                onClick = { onSpeciesClick(row.summary.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeciesRowSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SandCreme),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SandCreme),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.35f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SandCreme),
            )
        }
    }
}

@Composable
private fun FamilyHeader(
    family: String,
    familySv: String,
    locale: Locale,
    count: Int,
) {
    // Svenskt trivialnamn i SV-locale, latinskt familjenamn i EN (family_sv finns bara på svenska).
    val label = localizedFamilyLabel(locale, family, familySv)
    // T10b Important 4: TalkBack read the bare count Text ("3") with no context. clearAndSetSemantics
    // replaces the row's two child Texts with one clean "<family>, N arter/species" announcement,
    // built from the label BEFORE uppercasing (TalkBack shouldn't spell it out letter by letter).
    val description = pluralStringResource(Res.plurals.archive_family_header_description, count, label, count)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MossCreme)
                .hairlineBottom()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clearAndSetSemantics {
                    heading()
                    contentDescription = description
                },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            color = InkMuted,
            fontWeight = FontWeight.W600,
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            letterSpacing = 0.15.em,
        )
        Text(
            text = count.toString(),
            color = InkMuted,
            fontWeight = FontWeight.W600,
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            letterSpacing = 0.15.em,
        )
    }
}

@Composable
private fun ArchiveErrorBlock(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val caveat = rememberCaveat()
        val serif = rememberDmSerifDisplay()
        Text(
            stringResource(Res.string.archive_error_title),
            color = TextOnCreme,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.archive_error_body),
            color = MarginaliaInk,
            fontFamily = caveat,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = AccentCopper)) {
            Text(stringResource(Res.string.archive_error_retry), color = OffwhiteWarm)
        }
    }
}

@Composable
private fun JournalSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // T10c: reverted the T10b attempt at a persistent contentDescription here — a
        // contentDescription on an EDITABLE field can make TalkBack read the description
        // instead of the typed value (Accessibility Scanner's EditableContentDescCheck flags
        // exactly this). The placeholder alone (announced while empty) is the correct pattern.
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        shape = RoundedCornerShape(12.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = InkMuted,
            )
        },
        // T10b minor 12: Inter (the default sans), not Caveat — Caveat is reserved for marginalia
        // accents elsewhere, not a search hint.
        placeholder = {
            Text(
                text = placeholder,
                color = InkMuted,
                fontSize = 14.sp,
            )
        },
        trailingIcon =
            if (value.isNotEmpty()) {
                {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.archive_search_clear),
                            tint = AccentCopper,
                        )
                    }
                }
            } else {
                null
            },
        colors =
            OutlinedTextFieldDefaults.colors(
                // OutlineInk, not Hairline: Hairline measures 1.24–1.44:1 on our papers and fails
                // WCAG 1.4.11's 3:1 for an interactive input's boundary (T10b minor 13).
                unfocusedBorderColor = OutlineInk,
                focusedBorderColor = AccentCopper,
                unfocusedContainerColor = CardPaper,
                focusedContainerColor = CardPaper,
                cursorColor = AccentCopper,
            ),
    )
}

@Composable
private fun ChipBar(
    selected: ArchiveChip,
    onSelect: (ArchiveChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels =
        listOf(
            ArchiveChip.ALL to stringResource(Res.string.archive_chip_all),
            ArchiveChip.SONGBIRDS to stringResource(Res.string.archive_chip_songbirds),
            ArchiveChip.WATERFOWL to stringResource(Res.string.archive_chip_waterfowl),
            ArchiveChip.WADERS to stringResource(Res.string.archive_chip_waders),
            ArchiveChip.GULLS_TERNS to stringResource(Res.string.archive_chip_gulls_terns),
            ArchiveChip.AUKS to stringResource(Res.string.archive_chip_auks),
            ArchiveChip.SEABIRDS to stringResource(Res.string.archive_chip_seabirds),
            ArchiveChip.GREBES_DIVERS to stringResource(Res.string.archive_chip_grebes_divers),
            ArchiveChip.HERONS_STORKS to stringResource(Res.string.archive_chip_herons_storks),
            ArchiveChip.RAPTORS to stringResource(Res.string.archive_chip_raptors),
            ArchiveChip.OWLS to stringResource(Res.string.archive_chip_owls),
            ArchiveChip.GAMEBIRDS to stringResource(Res.string.archive_chip_gamebirds),
            ArchiveChip.DOVES to stringResource(Res.string.archive_chip_doves),
            ArchiveChip.WOODPECKERS to stringResource(Res.string.archive_chip_woodpeckers),
            ArchiveChip.CRANES_RAILS to stringResource(Res.string.archive_chip_cranes_rails),
            ArchiveChip.OTHER to stringResource(Res.string.archive_chip_other),
        )
    // T10b minor 9+10: ChipBar's chips are now BirdyPill's filter variant (selected != null) —
    // same padding/size as the action pill (SortChip), so the two rows match in height.
    // selectableGroup() tells TalkBack this LazyRow is one mutually-exclusive choice.
    LazyRow(
        modifier = modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(labels) { (chipValue, label) ->
            BirdyPill(
                text = label,
                onClick = { onSelect(chipValue) },
                selected = selected == chipValue,
            )
        }
    }
}

@Composable
private fun SortChip(
    sort: ArchiveSort,
    onClick: () -> Unit,
) {
    val label =
        when (sort) {
            ArchiveSort.ALPHA -> stringResource(Res.string.archive_sort_alpha)
            ArchiveSort.FAMILY -> stringResource(Res.string.archive_sort_family)
            ArchiveSort.RECENT -> stringResource(Res.string.archive_sort_recent)
        }
    BirdyPill(
        text = label,
        onClick = onClick,
        contentDescription = stringResource(Res.string.sort_chip_description, label),
    )
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun SpeciesRow(
    summary: se.birdy.content.model.SpeciesSummary,
    isStamped: Boolean,
    stampNumber: Int?,
    onClick: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .hairlineBottom()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbModifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SandCreme)
        val heroImagePath = summary.heroImagePath
        if (heroImagePath != null) {
            AsyncImage(
                model = speciesImageUri(heroImagePath),
                contentDescription = stringResource(Res.string.species_photo_label, summary.name),
                contentScale = ContentScale.Crop,
                modifier = thumbModifier,
            )
        } else {
            Box(modifier = thumbModifier)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.name,
                color = TextOnCreme,
                fontFamily = serif,
                fontSize = 16.sp,
                // T10b minor 8: up to 2 lines for long common names; the Latin name stays 1.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary.scientificName,
                color = InkMuted,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Spec gap C: iucn_status is the GLOBAL IUCN red list (not a national one).
        StatusTag(summary.iucnStatus)
        if (isStamped && stampNumber != null) {
            Spacer(Modifier.width(8.dp))
            MiniStamp(number = stampNumber, size = 26.dp)
        }
    }
}

/**
 * "Rödlistad" for a red-listed species, "Utdöd" for an extinct one (release 1.3.0 Task 7g:
 * Garfågel, Kanariestrandskata, Smalnäbbad spov), nothing otherwise; with its own leading gap.
 */
@Composable
private fun StatusTag(iucnStatus: String) {
    val text =
        when {
            isRedListed(iucnStatus) -> stringResource(Res.string.archive_red_listed_tag)
            isExtinct(iucnStatus) -> stringResource(Res.string.archive_extinct_tag)
            else -> null
        } ?: return
    Spacer(Modifier.width(8.dp))
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(RedListTagBg)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = StampNavy,
            fontWeight = FontWeight.W600,
            fontSize = 9.5.sp,
            // T10c: without this the pill inherits the theme's 22sp bodyLarge line height and
            // grows to ~28dp tall, reading as a button rather than a small tag.
            lineHeight = 12.sp,
            maxLines = 1,
            letterSpacing = 0.1.em,
        )
    }
}
