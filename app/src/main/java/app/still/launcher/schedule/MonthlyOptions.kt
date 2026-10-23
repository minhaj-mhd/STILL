package app.still.launcher.schedule

import java.time.LocalDate

/**
 * The monthly patterns that include [date], the way calendar apps offer them:
 * "on day 23", "on the 4th Friday", and "on the last Friday" when [date] falls in the
 * month's final seven days. A 5th weekday is only offered as "last".
 */
fun monthlyOptionsFor(date: LocalDate, interval: Int = 1): List<Recurrence> {
    val ordinal = (date.dayOfMonth - 1) / 7 + 1
    val isLast = date.dayOfMonth + 7 > date.lengthOfMonth()
    return buildList {
        add(Recurrence.MonthlyOnDay(date.dayOfMonth, interval))
        if (ordinal <= 4) add(Recurrence.MonthlyOnWeekday(ordinal, date.dayOfWeek, interval))
        if (isLast) add(Recurrence.MonthlyOnWeekday(-1, date.dayOfWeek, interval))
    }
}

/**
 * Moves a recurrence to a new start date, keeping its kind: a monthly-on-day repeat follows the
 * new day number, an ordinal-weekday repeat follows the new date's position in its month, and a
 * weekly repeat gains the new weekday if it had only one day.
 */
fun Recurrence.followingStart(newStart: LocalDate): Recurrence = when (this) {
    is Recurrence.MonthlyOnDay -> copy(day = newStart.dayOfMonth)
    is Recurrence.MonthlyOnWeekday -> {
        val options = monthlyOptionsFor(newStart, interval).filterIsInstance<Recurrence.MonthlyOnWeekday>()
        options.firstOrNull { (it.ordinal == -1) == (ordinal == -1) } ?: options.firstOrNull() ?: this
    }
    is Recurrence.Weekly -> if (days.size == 1) copy(days = setOf(newStart.dayOfWeek)) else this
    else -> this
}
