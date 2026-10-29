package app.still.launcher.productivity

import java.time.LocalTime

/** One row on the Today page's timeline. */
sealed interface TimelineItem {
    data class OfTask(val entry: TaskEntry) : TimelineItem
    data class OfHabit(val entry: HabitEntry) : TimelineItem

    /** Where the current time falls among the timed items. */
    data object Now : TimelineItem
}

/** The Today page as a day: late tasks, then the timed items along a line, then the rest. */
data class TodayTimeline(
    /** Overdue tasks, from earlier days. */
    val late: List<TaskEntry>,
    /** Tasks and habits due today at a time, in time order, with [TimelineItem.Now] among them. */
    val timed: List<TimelineItem>,
    /** Due today at no particular time: tasks first, then habits. */
    val anyTime: List<TimelineItem>,
    /** Tasks without a date. */
    val undated: List<TaskEntry>,
) {
    /** Whether there's a line to draw: anything late or at a time. */
    val hasLine: Boolean get() = late.isNotEmpty() || timed.isNotEmpty()
}

/**
 * Lays [TodayAgenda] out by time. Done items keep their place, so the day reads in order; at the
 * same time, tasks come before habits and higher priority first. [now] goes before the first
 * item after it, or last; it's left out when nothing has a time.
 */
fun TodayAgenda.timeline(now: LocalTime): TodayTimeline {
    val timedTasks = today.filter { it.occurrence?.time != null }.map { TimelineItem.OfTask(it) }
    val timedHabits = habits.filter { it.occurrence.time != null }.map { TimelineItem.OfHabit(it) }
    val timed = (timedTasks + timedHabits).sortedWith(
        compareBy<TimelineItem>(
            { it.time },
            { if (it is TimelineItem.OfTask) 0 else 1 },
            { -it.priorityOrdinal },
        ),
    )
    val withNow = if (timed.isEmpty()) {
        timed
    } else {
        val after = timed.indexOfFirst { it.time!! > now }.takeIf { it >= 0 } ?: timed.size
        timed.take(after) + TimelineItem.Now + timed.drop(after)
    }
    return TodayTimeline(
        late = overdue,
        timed = withNow,
        anyTime = today.filter { it.occurrence?.time == null }.map { TimelineItem.OfTask(it) } +
            habits.filter { it.occurrence.time == null }.map { TimelineItem.OfHabit(it) },
        undated = undated,
    )
}

private val TimelineItem.time: LocalTime?
    get() = when (this) {
        is TimelineItem.OfTask -> entry.occurrence?.time
        is TimelineItem.OfHabit -> entry.occurrence.time
        TimelineItem.Now -> null
    }

private val TimelineItem.priorityOrdinal: Int
    get() = when (this) {
        is TimelineItem.OfTask -> entry.task.priority.ordinal
        is TimelineItem.OfHabit -> entry.habit.priority.ordinal
        TimelineItem.Now -> 0
    }
