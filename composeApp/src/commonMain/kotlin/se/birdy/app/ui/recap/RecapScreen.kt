package se.birdy.app.ui.recap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.recap_cta_open_camera
import birdy_bird_scanner.composeapp.generated.resources.recap_dates_one_month_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_dates_two_months_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_delta_line
import birdy_bird_scanner.composeapp.generated.resources.recap_delta_same
import birdy_bird_scanner.composeapp.generated.resources.recap_eyebrow_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_headline_active
import birdy_bird_scanner.composeapp.generated.resources.recap_headline_quiet
import birdy_bird_scanner.composeapp.generated.resources.recap_load_error
import birdy_bird_scanner.composeapp.generated.resources.recap_quiet_encouragement
import birdy_bird_scanner.composeapp.generated.resources.recap_section_all_a11y_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_section_all_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_streak_label
import birdy_bird_scanner.composeapp.generated.resources.recap_streak_nudge_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_sub_active
import birdy_bird_scanner.composeapp.generated.resources.recap_sub_quiet
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackTopBar
import se.birdy.app.ui.components.BirdyPrimaryButton
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalSubLine
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.OrnamentRule
import se.birdy.app.ui.dailybird.monthFullRes
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat
import kotlin.math.abs

/**
 * "Veckans uppslag", the weekly recap (release 1.3.0 Task 7j, design "Uppslag 1", Albin's choice
 * 2026-10-07): the week's numbers in a header row like Mina arter's, a Monday-to-Sunday strip of
 * the days you were out, new species with their place in the life list, the week's stamp, and every
 * find as a grid that opens each find. A quiet week keeps the strip, so it still reads as a page of
 * the same journal, and asks for the week's first find.
 *
 * Reached from the recap card in Mina arter and from the Sunday evening notification.
 */
@Composable
fun RecapScreen(
    viewModel: RecapViewModel,
    onOpenCamera: () -> Unit,
    onObservationClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Reached from the recap card in Mina arter and from the weekly notification; it had no way
    // back but the system gesture (release 1.3.0 Task 7b). The bar stays put above the content.
    Column(modifier = Modifier.fillMaxSize().paperBackground()) {
        BackTopBar(onBack = onBack)
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (val s = state) {
                RecapUiState.Loading -> JournalLoading()
                is RecapUiState.Loaded -> RecapContent(s, onOpenCamera, onObservationClick)
                is RecapUiState.Error -> RecapError()
            }
        }
    }
}

@Composable
private fun RecapError() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.recap_load_error),
            color = MarginaliaInk,
            fontFamily = rememberCaveat(),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecapContent(
    state: RecapUiState.Loaded,
    onOpenCamera: () -> Unit,
    onObservationClick: (String) -> Unit,
) {
    val summary = state.recap.summary
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 8dp at the top: the back button's bar is already above it (Task 7b).
                .padding(start = RecapGutter, top = 8.dp, end = RecapGutter, bottom = 28.dp),
    ) {
        RecapIntro(state)
        if (!summary.isQuiet) {
            Spacer(Modifier.height(14.dp))
            RecapLedger(summary)
            DeltaLine(summary.deltaVsLastWeek)
        }
        OrnamentRule()
        WeekStrip(state.days)
        if (summary.isQuiet) {
            QuietBody(state, onOpenCamera)
        } else {
            ActiveBody(state, onObservationClick)
        }
    }
}

/** Kicker with the week number, the headline and the week's dates (with the days out). */
@Composable
private fun RecapIntro(state: RecapUiState.Loaded) {
    val summary = state.recap.summary
    MicroLabel(stringResource(Res.string.recap_eyebrow_fmt, summary.week.isoWeek.toString()))
    Spacer(Modifier.height(8.dp))
    JournalHeadline(
        text =
            stringResource(
                if (summary.isQuiet) Res.string.recap_headline_quiet else Res.string.recap_headline_active,
            ),
        fontSize = 32.sp,
        modifier = Modifier.semantics { heading() },
    )
    if (state.days.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        val dates = weekDatesLabel(state.days.first().date, state.days.last().date)
        val daysOut = state.days.count { it.findCount > 0 }
        JournalSubLine(
            if (summary.isQuiet) {
                stringResource(Res.string.recap_sub_quiet, dates)
            } else {
                pluralStringResource(Res.plurals.recap_sub_active, daysOut, dates, daysOut)
            },
        )
    }
}

/** "5 till 11 oktober", or "28 september till 4 oktober" for a week across two months. */
@Composable
internal fun weekDatesLabel(
    first: LocalDate,
    last: LocalDate,
): String =
    if (first.month == last.month) {
        stringResource(
            Res.string.recap_dates_one_month_fmt,
            first.dayOfMonth,
            last.dayOfMonth,
            stringResource(monthFullRes(last.month)),
        )
    } else {
        stringResource(
            Res.string.recap_dates_two_months_fmt,
            first.dayOfMonth,
            stringResource(monthFullRes(first.month)),
            last.dayOfMonth,
            stringResource(monthFullRes(last.month)),
        )
    }

/** "+3 fynd mot förra veckan", handwritten in rust under the header row. */
@Composable
private fun DeltaLine(delta: Int) {
    Text(
        text =
            if (delta == 0) {
                stringResource(Res.string.recap_delta_same)
            } else {
                pluralStringResource(Res.plurals.recap_delta_line, abs(delta), if (delta > 0) "+$delta" else "$delta")
            },
        color = AccentCopper,
        fontFamily = rememberCaveat(),
        fontSize = 18.sp,
        lineHeight = 1.15.em,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

@Composable
private fun ActiveBody(
    state: RecapUiState.Loaded,
    onObservationClick: (String) -> Unit,
) {
    if (state.newSpecies.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        NewSpeciesSection(state.newSpecies, onObservationClick)
    }
    Spacer(Modifier.height(16.dp))
    StampSection(state.stamps)
    Spacer(Modifier.height(20.dp))
    // Read as "Alla fynd, 7", not the caps with their middle dot.
    val allFinds = stringResource(Res.string.recap_section_all_a11y_fmt, state.finds.size)
    MicroLabel(
        text = stringResource(Res.string.recap_section_all_fmt, state.finds.size),
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                heading()
                contentDescription = allFinds
            },
    )
    Spacer(Modifier.height(10.dp))
    FindsGrid(state.finds, onObservationClick)
}

/** A week without finds: the empty strip above, then a nudge, the streak at risk and the camera. */
@Composable
private fun QuietBody(
    state: RecapUiState.Loaded,
    onOpenCamera: () -> Unit,
) {
    val summary = state.recap.summary
    val caveat = rememberCaveat()
    Spacer(Modifier.height(18.dp))
    Text(
        stringResource(Res.string.recap_quiet_encouragement),
        color = MarginaliaInk,
        fontFamily = caveat,
        fontSize = 21.sp,
        lineHeight = 1.15.em,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (summary.streakAtRisk) {
        Spacer(Modifier.height(16.dp))
        MicroLabel(stringResource(Res.string.recap_streak_label))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.recap_streak_nudge_fmt, summary.weeklyStreak.toString()),
            color = MarginaliaInk,
            fontFamily = caveat,
            fontSize = 19.sp,
            lineHeight = 1.15.em,
        )
    }
    // A stamp can still arrive in a week without finds (a new badge in an app update).
    if (state.stamps.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        StampSection(state.stamps)
    }
    Spacer(Modifier.height(20.dp))
    BirdyPrimaryButton(
        text = stringResource(Res.string.recap_cta_open_camera),
        onClick = onOpenCamera,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Side margin of the recap page. */
internal val RecapGutter = 20.dp
