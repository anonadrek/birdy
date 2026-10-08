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

internal fun weekdayShortRes(day: DayOfWeek): StringResource =
    when (day) {
        DayOfWeek.MONDAY -> Res.string.daily_bird_weekday_mon
        DayOfWeek.TUESDAY -> Res.string.daily_bird_weekday_tue
        DayOfWeek.WEDNESDAY -> Res.string.daily_bird_weekday_wed
        DayOfWeek.THURSDAY -> Res.string.daily_bird_weekday_thu
        DayOfWeek.FRIDAY -> Res.string.daily_bird_weekday_fri
        DayOfWeek.SATURDAY -> Res.string.daily_bird_weekday_sat
        DayOfWeek.SUNDAY -> Res.string.daily_bird_weekday_sun
    }

// Reuses the diary's short month names (the Lifelist month headers say the same "okt"/"Oct").
internal fun monthShortRes(month: Month): StringResource =
    when (month) {
        Month.JANUARY -> Res.string.diary_month_short_jan
        Month.FEBRUARY -> Res.string.diary_month_short_feb
        Month.MARCH -> Res.string.diary_month_short_mar
        Month.APRIL -> Res.string.diary_month_short_apr
        Month.MAY -> Res.string.diary_month_short_may
        Month.JUNE -> Res.string.diary_month_short_jun
        Month.JULY -> Res.string.diary_month_short_jul
        Month.AUGUST -> Res.string.diary_month_short_aug
        Month.SEPTEMBER -> Res.string.diary_month_short_sep
        Month.OCTOBER -> Res.string.diary_month_short_oct
        Month.NOVEMBER -> Res.string.diary_month_short_nov
        Month.DECEMBER -> Res.string.diary_month_short_dec
    }

internal fun weekdayFullRes(day: DayOfWeek): StringResource =
    when (day) {
        DayOfWeek.MONDAY -> Res.string.daily_bird_weekday_full_mon
        DayOfWeek.TUESDAY -> Res.string.daily_bird_weekday_full_tue
        DayOfWeek.WEDNESDAY -> Res.string.daily_bird_weekday_full_wed
        DayOfWeek.THURSDAY -> Res.string.daily_bird_weekday_full_thu
        DayOfWeek.FRIDAY -> Res.string.daily_bird_weekday_full_fri
        DayOfWeek.SATURDAY -> Res.string.daily_bird_weekday_full_sat
        DayOfWeek.SUNDAY -> Res.string.daily_bird_weekday_full_sun
    }

internal fun monthFullRes(month: Month): StringResource =
    when (month) {
        Month.JANUARY -> Res.string.daily_bird_month_full_jan
        Month.FEBRUARY -> Res.string.daily_bird_month_full_feb
        Month.MARCH -> Res.string.daily_bird_month_full_mar
        Month.APRIL -> Res.string.daily_bird_month_full_apr
        Month.MAY -> Res.string.daily_bird_month_full_may
        Month.JUNE -> Res.string.daily_bird_month_full_jun
        Month.JULY -> Res.string.daily_bird_month_full_jul
        Month.AUGUST -> Res.string.daily_bird_month_full_aug
        Month.SEPTEMBER -> Res.string.daily_bird_month_full_sep
        Month.OCTOBER -> Res.string.daily_bird_month_full_oct
        Month.NOVEMBER -> Res.string.daily_bird_month_full_nov
        Month.DECEMBER -> Res.string.daily_bird_month_full_dec
    }

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
