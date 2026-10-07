package se.birdy.app.ui.dailybird

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_date_fmt
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_date_full_fmt
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_apr
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_aug
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_dec
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_feb
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_jan
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_jul
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_jun
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_mar
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_may
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_nov
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_oct
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_month_full_sep
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_fri
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_full_fri
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_full_mon
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_full_sat
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_full_sun
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_full_thu
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_full_tue
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_full_wed
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_mon
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_sat
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_sun
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_thu
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_tue
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_wed
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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The pieces of the hero's date, as resources so each language writes its own names: short for
 * the kicker ("tis 6 okt"), full for TalkBack ("tisdag 6 oktober", abbreviations read badly).
 */
internal data class DailyBirdDateParts(
    val weekday: StringResource,
    val dayOfMonth: Int,
    val month: StringResource,
    val weekdayFull: StringResource,
    val monthFull: StringResource,
)

internal fun dailyBirdDateParts(date: LocalDate): DailyBirdDateParts =
    DailyBirdDateParts(
        weekday = weekdayShortRes(date.dayOfWeek),
        dayOfMonth = date.dayOfMonth,
        month = monthShortRes(date.month),
        weekdayFull = weekdayFullRes(date.dayOfWeek),
        monthFull = monthFullRes(date.month),
    )

// Lists indexed by ISO weekday (Monday = 1) and month number (January = 1), not `when` over
// DayOfWeek/Month: those are expect enums in kotlinx-datetime's common code, where a `when`
// without `else` doesn't compile (:composeApp:compileCommonMainKotlinMetadata).
private val weekdayShort =
    listOf(
        Res.string.daily_bird_weekday_mon,
        Res.string.daily_bird_weekday_tue,
        Res.string.daily_bird_weekday_wed,
        Res.string.daily_bird_weekday_thu,
        Res.string.daily_bird_weekday_fri,
        Res.string.daily_bird_weekday_sat,
        Res.string.daily_bird_weekday_sun,
    )

private val weekdayFull =
    listOf(
        Res.string.daily_bird_weekday_full_mon,
        Res.string.daily_bird_weekday_full_tue,
        Res.string.daily_bird_weekday_full_wed,
        Res.string.daily_bird_weekday_full_thu,
        Res.string.daily_bird_weekday_full_fri,
        Res.string.daily_bird_weekday_full_sat,
        Res.string.daily_bird_weekday_full_sun,
    )

// Reuses the diary's short month names (the Lifelist month headers say the same "okt"/"Oct").
private val monthShort =
    listOf(
        Res.string.diary_month_short_jan,
        Res.string.diary_month_short_feb,
        Res.string.diary_month_short_mar,
        Res.string.diary_month_short_apr,
        Res.string.diary_month_short_may,
        Res.string.diary_month_short_jun,
        Res.string.diary_month_short_jul,
        Res.string.diary_month_short_aug,
        Res.string.diary_month_short_sep,
        Res.string.diary_month_short_oct,
        Res.string.diary_month_short_nov,
        Res.string.diary_month_short_dec,
    )

private val monthFull =
    listOf(
        Res.string.daily_bird_month_full_jan,
        Res.string.daily_bird_month_full_feb,
        Res.string.daily_bird_month_full_mar,
        Res.string.daily_bird_month_full_apr,
        Res.string.daily_bird_month_full_may,
        Res.string.daily_bird_month_full_jun,
        Res.string.daily_bird_month_full_jul,
        Res.string.daily_bird_month_full_aug,
        Res.string.daily_bird_month_full_sep,
        Res.string.daily_bird_month_full_oct,
        Res.string.daily_bird_month_full_nov,
        Res.string.daily_bird_month_full_dec,
    )

internal fun weekdayShortRes(day: DayOfWeek): StringResource = weekdayShort[day.isoDayNumber - 1]

internal fun monthShortRes(month: Month): StringResource = monthShort[month.number - 1]

internal fun weekdayFullRes(day: DayOfWeek): StringResource = weekdayFull[day.isoDayNumber - 1]

internal fun monthFullRes(month: Month): StringResource = monthFull[month.number - 1]

/** "tisdag 6 oktober" / "Tuesday 6 October", for TalkBack. */
@Composable
internal fun dailyBirdDateA11yLabel(date: LocalDate): String {
    val parts = dailyBirdDateParts(date)
    return stringResource(
        Res.string.daily_bird_date_full_fmt,
        stringResource(parts.weekdayFull),
        parts.dayOfMonth,
        stringResource(parts.monthFull),
    )
}

/** "tis 6 okt" / "Tue 6 Oct". */
@Composable
internal fun dailyBirdDateLabel(date: LocalDate): String {
    val parts = dailyBirdDateParts(date)
    return stringResource(
        Res.string.daily_bird_date_fmt,
        stringResource(parts.weekday),
        parts.dayOfMonth,
        stringResource(parts.month),
    )
}
