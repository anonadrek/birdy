package se.birdy.app.ui.dailybird

import androidx.compose.runtime.Composable
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_date_fmt
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_fri
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

/** The pieces of the hero's short date, as resources so each language writes its own names. */
internal data class DailyBirdDateParts(
    val weekday: StringResource,
    val dayOfMonth: Int,
    val month: StringResource,
)

internal fun dailyBirdDateParts(date: LocalDate): DailyBirdDateParts =
    DailyBirdDateParts(
        weekday = weekdayShortRes(date.dayOfWeek),
        dayOfMonth = date.dayOfMonth,
        month = monthShortRes(date.month),
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
