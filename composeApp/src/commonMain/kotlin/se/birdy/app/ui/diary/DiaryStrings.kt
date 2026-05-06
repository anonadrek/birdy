package se.birdy.app.ui.diary

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_unknown_species
import birdy_bird_scanner.composeapp.generated.resources.diary_month_apr
import birdy_bird_scanner.composeapp.generated.resources.diary_month_aug
import birdy_bird_scanner.composeapp.generated.resources.diary_month_dec
import birdy_bird_scanner.composeapp.generated.resources.diary_month_feb
import birdy_bird_scanner.composeapp.generated.resources.diary_month_header_format
import birdy_bird_scanner.composeapp.generated.resources.diary_month_jan
import birdy_bird_scanner.composeapp.generated.resources.diary_month_jul
import birdy_bird_scanner.composeapp.generated.resources.diary_month_jun
import birdy_bird_scanner.composeapp.generated.resources.diary_month_mar
import birdy_bird_scanner.composeapp.generated.resources.diary_month_may
import birdy_bird_scanner.composeapp.generated.resources.diary_month_nov
import birdy_bird_scanner.composeapp.generated.resources.diary_month_oct
import birdy_bird_scanner.composeapp.generated.resources.diary_month_sep
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_apr
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_aug
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_dec
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_feb
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_jan
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_jul
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_jun
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_mar
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_may
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_nov
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_oct
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_sep
import birdy_bird_scanner.composeapp.generated.resources.diary_relative_date_full
import birdy_bird_scanner.composeapp.generated.resources.diary_relative_today
import birdy_bird_scanner.composeapp.generated.resources.diary_relative_yesterday
import org.jetbrains.compose.resources.stringResource

@Composable
fun monthShortUpper(month1Based: Int): String =
    stringResource(
        when (month1Based) {
            1 -> Res.string.diary_month_jan
            2 -> Res.string.diary_month_feb
            3 -> Res.string.diary_month_mar
            4 -> Res.string.diary_month_apr
            5 -> Res.string.diary_month_may
            6 -> Res.string.diary_month_jun
            7 -> Res.string.diary_month_jul
            8 -> Res.string.diary_month_aug
            9 -> Res.string.diary_month_sep
            10 -> Res.string.diary_month_oct
            11 -> Res.string.diary_month_nov
            12 -> Res.string.diary_month_dec
            else -> error("Invalid month $month1Based")
        },
    )

@Composable
fun monthShortLower(month1Based: Int): String =
    stringResource(
        when (month1Based) {
            1 -> Res.string.diary_month_short_jan
            2 -> Res.string.diary_month_short_feb
            3 -> Res.string.diary_month_short_mar
            4 -> Res.string.diary_month_short_apr
            5 -> Res.string.diary_month_short_may
            6 -> Res.string.diary_month_short_jun
            7 -> Res.string.diary_month_short_jul
            8 -> Res.string.diary_month_short_aug
            9 -> Res.string.diary_month_short_sep
            10 -> Res.string.diary_month_short_oct
            11 -> Res.string.diary_month_short_nov
            12 -> Res.string.diary_month_short_dec
            else -> error("Invalid month $month1Based")
        },
    )

@Composable
fun formatMonthHeader(
    year: Int,
    month1Based: Int,
): String = stringResource(Res.string.diary_month_header_format, monthShortUpper(month1Based), year)

@Composable
fun formatRelativeDateText(text: RelativeDateText): String =
    when (text) {
        is RelativeDateText.Today -> stringResource(Res.string.diary_relative_today, text.timeOfDay)
        is RelativeDateText.Yesterday -> stringResource(Res.string.diary_relative_yesterday, text.timeOfDay)
        is RelativeDateText.WithinWeek ->
            stringResource(Res.string.diary_relative_date_full, text.dayOfMonth, monthShortLower(text.month1Based), text.timeOfDay)
        is RelativeDateText.Full ->
            stringResource(Res.string.diary_relative_date_full, text.dayOfMonth, monthShortLower(text.month1Based), text.timeOfDay)
    }

@Composable
fun resolveSpeciesName(rawName: String): String =
    if (rawName == DiaryItem.UNKNOWN_SPECIES_PLACEHOLDER) {
        stringResource(Res.string.diary_detail_unknown_species)
    } else {
        rawName
    }
