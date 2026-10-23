package app.still.launcher.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.still.launcher.R
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.monthlyOptionsFor
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.DatePickerDialog
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.Stepper
import app.still.launcher.ui.components.WeekdayPicker
import app.still.launcher.ui.components.formatShortDate
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Picks how a task repeats from its [start] date: never, every N days, chosen weekdays every
 * N weeks, monthly (by day or by weekday, derived from [start]), or yearly, plus an end date.
 */
@Composable
fun RepeatEditor(
    start: LocalDate,
    recurrence: Recurrence,
    until: LocalDate?,
    onChange: (Recurrence, LocalDate?) -> Unit,
) {
    var pickingEnd by rememberSaveable { mutableStateOf(false) }

    Column {
        FieldLabel(stringResource(R.string.repeat_title))
        ChipRow {
            Chip(stringResource(R.string.repeat_never), recurrence == Recurrence.Once) { onChange(Recurrence.Once, null) }
            Chip(stringResource(R.string.repeat_daily), recurrence is Recurrence.Daily) {
                if (recurrence !is Recurrence.Daily) onChange(Recurrence.Daily(), until)
            }
            Chip(stringResource(R.string.repeat_weekly), recurrence is Recurrence.Weekly) {
                if (recurrence !is Recurrence.Weekly) onChange(Recurrence.Weekly(setOf(start.dayOfWeek)), until)
            }
            Chip(stringResource(R.string.repeat_monthly), recurrence.isMonthly) {
                if (!recurrence.isMonthly) onChange(Recurrence.MonthlyOnDay(start.dayOfMonth), until)
            }
            Chip(stringResource(R.string.repeat_yearly), recurrence == Recurrence.Yearly) { onChange(Recurrence.Yearly, until) }
        }

        when (recurrence) {
            is Recurrence.Daily -> Stepper(
                value = recurrence.interval,
                onChange = { onChange(recurrence.copy(interval = it), until) },
                range = 1..365,
                label = pluralStringResource(R.plurals.repeat_every_days, recurrence.interval, recurrence.interval),
            )
            is Recurrence.Weekly -> {
                Stepper(
                    value = recurrence.interval,
                    onChange = { onChange(recurrence.copy(interval = it), until) },
                    range = 1..52,
                    label = pluralStringResource(R.plurals.repeat_every_weeks, recurrence.interval, recurrence.interval),
                )
                WeekdayPicker(recurrence.days) { days ->
                    if (days.isNotEmpty()) onChange(recurrence.copy(days = days), until)
                }
            }
            is Recurrence.MonthlyOnDay, is Recurrence.MonthlyOnWeekday -> {
                val interval = recurrence.monthlyInterval
                Stepper(
                    value = interval,
                    onChange = { onChange(recurrence.withMonthlyInterval(it), until) },
                    range = 1..24,
                    label = pluralStringResource(R.plurals.repeat_every_months, interval, interval),
                )
                monthlyOptionsFor(start, interval).forEach { option ->
                    RadioRow(monthlyLabel(option), selected = option == recurrence) { onChange(option, until) }
                }
            }
            Recurrence.Once, Recurrence.Yearly -> Unit
        }

        if (recurrence != Recurrence.Once) {
            FieldLabel(stringResource(R.string.repeat_ends))
            ChipRow {
                Chip(stringResource(R.string.repeat_ends_never), until == null) { onChange(recurrence, null) }
                val endLabel = until?.let { stringResource(R.string.repeat_ends_on, formatShortDate(it)) }
                Chip(endLabel ?: stringResource(R.string.repeat_ends_pick), until != null) { pickingEnd = true }
            }
        }
    }

    if (pickingEnd) {
        DatePickerDialog(
            initial = until ?: start.plusMonths(1),
            onDismiss = { pickingEnd = false },
            onPick = { date ->
                onChange(recurrence, maxOf(date, start))
                pickingEnd = false
            },
        )
    }
}

private val Recurrence.isMonthly: Boolean
    get() = this is Recurrence.MonthlyOnDay || this is Recurrence.MonthlyOnWeekday

private val Recurrence.monthlyInterval: Int
    get() = when (this) {
        is Recurrence.MonthlyOnDay -> interval
        is Recurrence.MonthlyOnWeekday -> interval
        else -> 1
    }

private fun Recurrence.withMonthlyInterval(interval: Int): Recurrence = when (this) {
    is Recurrence.MonthlyOnDay -> copy(interval = interval)
    is Recurrence.MonthlyOnWeekday -> copy(interval = interval)
    else -> this
}

@Composable
private fun monthlyLabel(option: Recurrence): String = when (option) {
    is Recurrence.MonthlyOnDay -> stringResource(R.string.repeat_on_day, option.day)
    is Recurrence.MonthlyOnWeekday -> {
        val ordinal = if (option.ordinal == -1) stringResource(R.string.repeat_ordinal_last)
        else stringArrayResource(R.array.repeat_ordinals)[option.ordinal - 1]
        val weekday = option.weekday.getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale)
        stringResource(R.string.repeat_on_ordinal, ordinal, weekday)
    }
    else -> ""
}
