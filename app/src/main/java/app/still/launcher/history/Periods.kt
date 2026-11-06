package app.still.launcher.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Insights looks back a week or a month at a time. */
enum class PeriodKind { WEEK, MONTH }

/**
 * The week or month [offset] periods back from the one holding [today] (0 is the current one,
 * -1 the one before). Weeks begin on [firstDayOfWeek].
 */
fun periodDays(kind: PeriodKind, offset: Int, today: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> = when (kind) {
    PeriodKind.WEEK -> {
        val start = weekStart(today, firstDayOfWeek).plusWeeks(offset.toLong())
        start..start.plusDays(6)
    }
    PeriodKind.MONTH -> {
        val start = today.withDayOfMonth(1).plusMonths(offset.toLong())
        start..start.plusMonths(1).minusDays(1)
    }
}

fun weekStart(day: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

/** A day of use as Insights reads it: a saved day, or today so far. */
data class HistoryDay(
    val day: LocalDate,
    val apps: Map<String, AppDay>,
    val unlocks: Int,
    /** Time in apps in each clock hour, 0 to 23. */
    val hourMillis: List<Long>,
    val limit: DayLimit,
)

/** One app over a week or month. */
data class AppTotal(
    val packageName: String,
    val millis: Long,
    val opens: Int,
    /** Its time on an average day, and on an average day the period before (null with nothing saved then). */
    val perDayMillis: Long,
    val perDayBeforeMillis: Long?,
)

/** A week or month of phone use, counting only the apps Insights shows. */
data class PeriodUsage(
    /** Each day's time from the first day to the last; null for days with nothing saved. */
    val dayMillis: List<Long?>,
    /** Which of those days went over the daily limit. */
    val overLimit: List<Boolean>,
    val totalMillis: Long,
    /** Time on an average day, and on an average day the period before; null without days to go on. */
    val perDayMillis: Long?,
    val perDayBeforeMillis: Long?,
    /** Busiest first. */
    val apps: List<AppTotal>,
    /** Time in each hour of the day, starting from the hour days start at. */
    val hourMillis: List<Long>,
    val unlocksPerDay: Int?,
    val unlocksBeforePerDay: Int?,
    /** The daily limit on the latest day with one; null while it's off. */
    val limitMillis: Long?,
) {
    val hasDays: Boolean get() = dayMillis.any { it != null }
    val overLimitDays: Int get() = overLimit.count { it }
}

/**
 * Sums the [days] from [from] to [to] for the apps [shown] lets through, against the [before]
 * period. Averages leave out [today] while it's still going, unless it's the only day there is.
 */
fun periodUsage(
    from: LocalDate,
    to: LocalDate,
    days: List<HistoryDay>,
    before: List<HistoryDay>,
    today: LocalDate,
    dayStartHour: Int,
    shown: (String) -> Boolean,
): PeriodUsage {
    val byDay = days.filter { it.day in from..to }.associateBy { it.day }
    val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
    fun HistoryDay.millis(): Long = apps.filterKeys(shown).values.sumOf { it.millis }
    val averaged = averagedDays(byDay.values, today)
    val averagedBefore = averagedDays(before, today)

    val packages = byDay.values.flatMap { day -> day.apps.filter { (name, app) -> shown(name) && app.millis > 0 }.keys }.toSet()
    val apps = packages.map { name ->
        AppTotal(
            packageName = name,
            millis = byDay.values.sumOf { it.apps[name]?.millis ?: 0L },
            opens = byDay.values.sumOf { it.apps[name]?.opens ?: 0 },
            perDayMillis = averaged.sumOf { it.apps[name]?.millis ?: 0L } / averaged.size.coerceAtLeast(1),
            perDayBeforeMillis = averagedBefore.takeIf { it.isNotEmpty() }?.let { days -> days.sumOf { it.apps[name]?.millis ?: 0L } / days.size },
        )
    }.sortedByDescending { it.millis }

    val hours = LongArray(HOURS)
    byDay.values.forEach { day -> day.hourMillis.forEachIndexed { hour, millis -> if (hour < HOURS) hours[hour] += millis } }

    return PeriodUsage(
        dayMillis = dates.map { byDay[it]?.millis() },
        overLimit = dates.map { byDay[it]?.limit?.isOver == true },
        totalMillis = byDay.values.sumOf { it.millis() },
        perDayMillis = averaged.takeIf { it.isNotEmpty() }?.let { days -> days.sumOf { it.millis() } / days.size },
        perDayBeforeMillis = averagedBefore.takeIf { it.isNotEmpty() }?.let { days -> days.sumOf { it.millis() } / days.size },
        apps = apps,
        hourMillis = List(HOURS) { hours[(dayStartHour + it) % HOURS] },
        unlocksPerDay = unlocksPerDay(averaged),
        unlocksBeforePerDay = unlocksPerDay(averagedBefore),
        limitMillis = byDay.values.maxByOrNull { it.day }?.limit?.limitMillis?.takeIf { it > 0 },
    )
}

/** The days an average is taken over: [days] without [today] while it's still going, unless it's all there is. */
private fun averagedDays(days: Collection<HistoryDay>, today: LocalDate): List<HistoryDay> =
    days.filter { it.day != today }.ifEmpty { days.toList() }

private fun unlocksPerDay(days: List<HistoryDay>): Int? =
    days.takeIf { it.isNotEmpty() }?.let { (it.sumOf { day -> day.unlocks }.toDouble() / it.size).roundToInt() }

/** A week within a month, by its first day, with the time on an average day of it. */
data class WeekAverage(val start: LocalDate, val perDayMillis: Long)

/**
 * The weeks that touch the month [from] to [to], each averaged over all its saved [days], even
 * those outside the month. Weeks with nothing saved are left out.
 */
fun weekAverages(
    from: LocalDate,
    to: LocalDate,
    days: List<HistoryDay>,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    shown: (String) -> Boolean,
): List<WeekAverage> =
    generateSequence(weekStart(from, firstDayOfWeek)) { it.plusWeeks(1) }
        .takeWhile { it <= to }
        .mapNotNull { start ->
            val week = averagedDays(days.filter { it.day >= start && it.day < start.plusWeeks(1) }, today)
            if (week.isEmpty()) return@mapNotNull null
            WeekAverage(start, week.sumOf { day -> day.apps.filterKeys(shown).values.sumOf { it.millis } } / week.size)
        }
        .toList()

/** How full a cell is, 0 for nothing and 1 to [LEVELS] against the [max] of its kind. */
fun level(value: Long, max: Long): Int =
    if (value <= 0 || max <= 0) 0 else ceil(LEVELS * value.toDouble() / max).toInt().coerceIn(1, LEVELS)

const val LEVELS = 4
