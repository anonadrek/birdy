package se.birdy.app.ui.diary

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.diary_confidence_format
import birdy_bird_scanner.composeapp.generated.resources.diary_empty_cta
import birdy_bird_scanner.composeapp.generated.resources.diary_empty_title
import birdy_bird_scanner.composeapp.generated.resources.diary_title
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    onObservationClick: (id: String) -> Unit,
    onScanCtaClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.background(MossCreme),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.diary_title)) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = HeroMossLight,
                        titleContentColor = TextOnHero,
                    ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MossCreme)) {
            when (val s = state) {
                DiaryUiState.Loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentCopper,
                    )
                DiaryUiState.Empty -> EmptyView(onScanCtaClick = onScanCtaClick)
                is DiaryUiState.Loaded -> LoadedList(state = s, onObservationClick = onObservationClick)
            }
        }
    }
}

@Composable
private fun EmptyView(onScanCtaClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(Res.string.diary_empty_title),
            style = MaterialTheme.typography.bodyLarge,
            color = TextOnCreme,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(16.dp))
        Button(
            onClick = onScanCtaClick,
            colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = TextOnHero),
        ) {
            Text(stringResource(Res.string.diary_empty_cta))
        }
    }
}

@Composable
private fun LoadedList(
    state: DiaryUiState.Loaded,
    onObservationClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        state.months.forEach { group ->
            item(key = "h-${group.year}-${group.month1Based}") {
                MonthHeader(year = group.year, month1Based = group.month1Based)
            }
            items(group.items, key = { it.observationId }) { item ->
                ObservationRow(item = item, onClick = { onObservationClick(item.observationId) })
            }
        }
    }
}

@Composable
private fun MonthHeader(
    year: Int,
    month1Based: Int,
) {
    Text(
        text = formatMonthHeader(year = year, month1Based = month1Based),
        color = HeroMossLight,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ObservationRow(
    item: DiaryItem,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .background(MossCreme)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = "file://${item.photoPath}",
                contentDescription = null,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SandCreme),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resolveSpeciesName(item.speciesName),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextOnCreme,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatRelativeDateText(item.relativeDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = HeroMossLight,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.diary_confidence_format, "${item.confidencePct}%"),
                style = MaterialTheme.typography.labelSmall,
                color = AccentCopper,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider(color = SandCreme, thickness = 1.dp)
    }
}
