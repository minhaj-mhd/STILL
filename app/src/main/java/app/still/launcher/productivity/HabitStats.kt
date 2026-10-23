package app.still.launcher.productivity

import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.toOccurrence
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.QuotaPeriod
import app.still.launcher.schedule.Schedule
import app.still.launcher.schedule.occurrencesBetween
import app.still.launcher.schedule.occursOn
import app.still.launcher.schedule.rangeContaining
import java.time.LocalDate
import java.time.ZoneId

/** How one day looks in a habit's history. */
enum class DayStatus {
    /** Every occurrence that day met the target. */
    DONE,

    /** Some progress, but not the full target. */
    PARTIAL,

    /** Excused with "skip today". Breaks a streak like a miss does. */
    SKIPPED,

    MISSED,

    /** Today, not finished yet. Doesn't break a streak. */
    PENDING,

    /** Not scheduled that day. */
    NOT_DUE,
}

/** What a streak counts. */
enum class StreakUnit { OCCURRENCES, WEEKS, MONTHS }

data class HabitStats(
    /** Unbroken run up to now, in [unit]s. */
    val currentStreak: Int,
    val bestStreak: Int,
    val unit: StreakUnit,
    /** Share of due occurrences done in the last 30 days; null for quota habits or when nothing was due. */
    val recentRate: Double?,
    /** Status of each of the last [HISTORY_DAYS] days, oldest first. */
    val history: List<Pair<LocalDate, DayStatus>>,
)

const val HISTORY_DAYS = 7 * 16L
private const val RECENT_DAYS = 30L

/**
 * Streaks and history for one habit from all of its [records]. A scheduled habit counts days
 * it was due (days off don't break a streak); a quota-only habit counts weeks or months in
 * which the quota was met.
 */
fun habitStats(
    habit: Habit,
    records: List<OccurrenceRecord>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): HabitStats {
    val schedule = habit.schedule
    val quota = habit.quota
    return when {
        schedule != null -> scheduledStats(habit, schedule, records, today)
        quota != null -> quotaStats(habit, quota, records, today, zone)
        else -> error("A habit needs a schedule or a quota")
    }
}

private fun scheduledStats(habit: Habit, schedule: Schedule, records: List<OccurrenceRecord>, today: LocalDate): HabitStats {
    val byDate = records.groupBy { it.date }

    fun statusOn(date: LocalDate): DayStatus {
        if (!schedule.occursOn(date)) return DayStatus.NOT_DUE
        val dayRecords = byDate[date].orEmpty()
        val slots = schedule.occurrencesBetween(date, date).map { occurrence ->
            dayRecords.firstOrNull { it.toOccurrence() == occurrence }
        }
        return when {
            slots.all { it != null && !it.skipped && it.value >= habit.target } -> DayStatus.DONE
            slots.any { it?.skipped == true } -> DayStatus.SKIPPED
            date == today -> DayStatus.PENDING
            slots.any { (it?.value ?: 0.0) > 0.0 } -> DayStatus.PARTIAL
            else -> DayStatus.MISSED
        }
    }

    val dueStatuses = if (schedule.start > today) {
        emptyList()
    } else {
        generateSequence(schedule.start) { it.plusDays(1) }
            .takeWhile { it <= today }
            .map { it to statusOn(it) }
            .filter { it.second != DayStatus.NOT_DUE }
            .toList()
    }
    val counted = dueStatuses.map { it.second }.filter { it != DayStatus.PENDING }

    val recent = dueStatuses.filter { it.first > today.minusDays(RECENT_DAYS) && it.second != DayStatus.PENDING }
    val historyStart = today.minusDays(HISTORY_DAYS - 1)

    return HabitStats(
        currentStreak = counted.asReversed().takeWhile { it == DayStatus.DONE }.size,
        bestStreak = longestRun(counted) { it == DayStatus.DONE },
        unit = StreakUnit.OCCURRENCES,
        recentRate = recent.takeIf { it.isNotEmpty() }?.let { days -> days.count { it.second == DayStatus.DONE }.toDouble() / days.size },
        history = generateSequence(historyStart) { it.plusDays(1) }.takeWhile { it <= today }
            .map { date -> date to statusOn(date) }
            .toList(),
    )
}

private fun quotaStats(
    habit: Habit,
    quota: Quota,
    records: List<OccurrenceRecord>,
    today: LocalDate,
    zone: ZoneId,
): HabitStats {
    val doneDates = records.filter { !it.skipped && it.value >= habit.target }.map { it.date }.toSet()
    val created = habit.createdAt.atZone(zone).toLocalDate()
    val start = minOf(created, doneDates.minOrNull() ?: created, today)

    // Whether each period from the first one to the current one met the quota.
    val periods = generateSequence(quota.period.rangeContaining(start)) { quota.period.rangeContaining(it.endInclusive.plusDays(1)) }
        .takeWhile { it.start <= today }
        .map { period -> doneDates.count { it in period } >= quota.count }
        .toList()
    // The current period only counts once it's met; until then it's still in progress.
    val counted = if (periods.lastOrNull() == true) periods else periods.dropLast(1)

    val historyStart = today.minusDays(HISTORY_DAYS - 1)
    return HabitStats(
        currentStreak = counted.asReversed().takeWhile { it }.size,
        bestStreak = longestRun(counted) { it },
        unit = if (quota.period == QuotaPeriod.WEEK) StreakUnit.WEEKS else StreakUnit.MONTHS,
        recentRate = null,
        history = generateSequence(historyStart) { it.plusDays(1) }.takeWhile { it <= today }
            .map { date -> date to if (date in doneDates) DayStatus.DONE else DayStatus.NOT_DUE }
            .toList(),
    )
}

private fun <T> longestRun(items: List<T>, predicate: (T) -> Boolean): Int {
    var best = 0
    var run = 0
    for (item in items) {
        run = if (predicate(item)) run + 1 else 0
        best = maxOf(best, run)
    }
    return best
}
