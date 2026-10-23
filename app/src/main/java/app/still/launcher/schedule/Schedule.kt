@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class, DayOfWeekSerializer::class)

package app.still.launcher.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * When something repeats. Shared by tasks, habits and (later) blocking schedules.
 * Every pattern counts from [Schedule.start]; intervals are always at least 1.
 */
@Serializable
sealed interface Recurrence {

    /** Only on the start date. */
    @Serializable
    @SerialName("once")
    data object Once : Recurrence

    /** Every [interval] days. */
    @Serializable
    @SerialName("daily")
    data class Daily(val interval: Int = 1) : Recurrence {
        init { require(interval >= 1) }
    }

    /**
     * On [days] of every [interval]th week, counting weeks (Monday to Sunday) from the one
     * containing the start date. Interval 2 gives "every other week".
     */
    @Serializable
    @SerialName("weekly")
    data class Weekly(val days: Set<DayOfWeek>, val interval: Int = 1) : Recurrence {
        init { require(interval >= 1 && days.isNotEmpty()) }
    }

    /** On [day] of every [interval]th month; shorter months use their last day (31 → 30, 28 or 29). */
    @Serializable
    @SerialName("monthly_day")
    data class MonthlyOnDay(val day: Int, val interval: Int = 1) : Recurrence {
        init { require(interval >= 1 && day in 1..31) }
    }

    /** On the [ordinal]th [weekday] of every [interval]th month; ordinal -1 means the last one. */
    @Serializable
    @SerialName("monthly_weekday")
    data class MonthlyOnWeekday(val ordinal: Int, val weekday: DayOfWeek, val interval: Int = 1) : Recurrence {
        init { require(interval >= 1 && (ordinal in 1..4 || ordinal == -1)) }
    }

    /** Every year on the start date's month and day; 29 February falls on the 28th in other years. */
    @Serializable
    @SerialName("yearly")
    data object Yearly : Recurrence
}

@Serializable
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init { require(start <= endInclusive) }

    operator fun contains(date: LocalDate): Boolean = date >= start && date <= endInclusive
}

@Serializable
data class Schedule(
    val start: LocalDate,
    val recurrence: Recurrence = Recurrence.Once,
    /** Last day it can occur, inclusive; null repeats forever. */
    val until: LocalDate? = null,
    /** Time slots on each day it occurs, e.g. 08:00 and 20:00. Empty means once that day, at any time. */
    val times: List<LocalTime> = emptyList(),
    /** Single days left out without changing the pattern. */
    val skippedDates: Set<LocalDate> = emptySet(),
    /** Stretches with no occurrences, e.g. a holiday. */
    val pauses: List<DateRange> = emptyList(),
) {
    val isRecurring: Boolean get() = recurrence != Recurrence.Once
}

/** One time something is due: a day, plus a time slot when the schedule has [Schedule.times]. */
data class Occurrence(val date: LocalDate, val time: LocalTime? = null) : Comparable<Occurrence> {
    override fun compareTo(other: Occurrence): Int =
        compareValuesBy(this, other, { it.date }, { it.time ?: LocalTime.MIN })
}

/** What happens to an occurrence that wasn't done by the end of its day. */
enum class MissedPolicy {
    /** It stays pending (overdue) until done. Recurring items carry only their latest missed occurrence. */
    CARRY_OVER,

    /** It counts as missed and stops being pending. Habits always work this way. */
    MISS,
}

fun Schedule.occursOn(date: LocalDate): Boolean =
    date >= start &&
        (until == null || date <= until) &&
        date !in skippedDates &&
        pauses.none { date in it } &&
        recurrence.matches(start, date)

/** Occurrences from [from] to [to], both inclusive, in order. */
fun Schedule.occurrencesBetween(from: LocalDate, to: LocalDate): List<Occurrence> {
    val first = maxOf(from, start)
    val last = until?.let { minOf(to, it) } ?: to
    if (first > last) return emptyList()
    return generateSequence(first) { it.plusDays(1) }
        .takeWhile { it <= last }
        .filter { occursOn(it) }
        .flatMap { date -> occurrencesOn(date) }
        .toList()
}

/** The first day on or after [date] that it occurs, looking ahead at most [searchDays]. */
fun Schedule.nextDate(date: LocalDate, searchDays: Long = MAX_SEARCH_DAYS): LocalDate? =
    generateSequence(maxOf(date, start)) { it.plusDays(1) }
        .takeWhile { ChronoUnit.DAYS.between(date, it) <= searchDays && (until == null || it <= until) }
        .firstOrNull { occursOn(it) }

/** The last day before [date] that it occurred, looking back at most [searchDays]. */
fun Schedule.previousDate(date: LocalDate, searchDays: Long = MAX_SEARCH_DAYS): LocalDate? =
    generateSequence(date.minusDays(1)) { it.minusDays(1) }
        .takeWhile { it >= start && ChronoUnit.DAYS.between(it, date) <= searchDays }
        .firstOrNull { occursOn(it) }

/**
 * Occurrences that still need doing on [today]: today's ones not in [done], plus, under
 * [MissedPolicy.CARRY_OVER], earlier ones not done.
 *
 * A one-time item stays overdue from its date until done. A recurring item carries over only
 * the day it last occurred before today; anything older counts as missed, so a skipped week
 * doesn't pile up seven overdue copies.
 */
fun Schedule.pending(
    today: LocalDate,
    done: Set<Occurrence>,
    policy: MissedPolicy,
): List<Occurrence> {
    val dueToday = if (occursOn(today)) occurrencesOn(today) else emptyList()
    val overdue = when {
        policy == MissedPolicy.MISS -> emptyList()
        !isRecurring -> if (start < today && occursOn(start)) occurrencesOn(start) else emptyList()
        else -> previousDate(today)?.let { occurrencesOn(it) } ?: emptyList()
    }
    return (overdue + dueToday).filterNot { it in done }
}

private fun Schedule.occurrencesOn(date: LocalDate): List<Occurrence> =
    if (times.isEmpty()) listOf(Occurrence(date)) else times.sorted().map { Occurrence(date, it) }

/** Whether the bare pattern lands on [date]; callers have already checked date >= start. */
private fun Recurrence.matches(start: LocalDate, date: LocalDate): Boolean = when (this) {
    Recurrence.Once -> date == start
    is Recurrence.Daily -> ChronoUnit.DAYS.between(start, date) % interval == 0L
    is Recurrence.Weekly -> date.dayOfWeek in days && weeksBetween(start, date) % interval == 0L
    is Recurrence.MonthlyOnDay ->
        monthsBetween(start, date) % interval == 0L && date.dayOfMonth == minOf(day, date.lengthOfMonth())
    is Recurrence.MonthlyOnWeekday ->
        monthsBetween(start, date) % interval == 0L && date == nthWeekdayOfMonth(date, ordinal, weekday)
    Recurrence.Yearly ->
        date.month == start.month && date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
}

private fun weeksBetween(start: LocalDate, date: LocalDate): Long {
    val monday = TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
    return ChronoUnit.WEEKS.between(start.with(monday), date.with(monday))
}

private fun monthsBetween(start: LocalDate, date: LocalDate): Long =
    (date.year - start.year) * 12L + (date.monthValue - start.monthValue)

private fun nthWeekdayOfMonth(inMonth: LocalDate, ordinal: Int, weekday: DayOfWeek): LocalDate =
    if (ordinal == -1) inMonth.with(TemporalAdjusters.lastInMonth(weekday))
    else inMonth.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, weekday))

/** Ten years: covers a yearly 29 February and any sensible interval. */
private const val MAX_SEARCH_DAYS = 3660L
