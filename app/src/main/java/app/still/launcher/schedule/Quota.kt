package app.still.launcher.schedule

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class QuotaPeriod { WEEK, MONTH }

/** "[count] times per [period]", on whichever days you like, e.g. swim 3 times a week. */
@Serializable
data class Quota(val count: Int, val period: QuotaPeriod) {
    init { require(count >= 1) }
}

/** The week (Monday to Sunday) or calendar month that contains [date]. */
fun QuotaPeriod.rangeContaining(date: LocalDate): DateRange = when (this) {
    QuotaPeriod.WEEK -> DateRange(
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
    )
    QuotaPeriod.MONTH -> DateRange(
        date.with(TemporalAdjusters.firstDayOfMonth()),
        date.with(TemporalAdjusters.lastDayOfMonth()),
    )
}

/** Sessions still needed this period after [done] so far; never negative. */
fun Quota.remaining(done: Int): Int = (count - done).coerceAtLeast(0)

/**
 * True when every day left in the period, [today] included, is needed to reach the quota.
 * That's the point where skipping today would make the goal impossible, so it's the only
 * time a quota habit counts as pending (and can gate apps).
 */
fun Quota.isBehindPace(done: Int, today: LocalDate): Boolean {
    val needed = remaining(done)
    if (needed == 0) return false
    val daysLeft = ChronoUnit.DAYS.between(today, period.rangeContaining(today).endInclusive) + 1
    return needed >= daysLeft
}
