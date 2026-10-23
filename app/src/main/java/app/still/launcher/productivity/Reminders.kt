package app.still.launcher.productivity

import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.Task
import app.still.launcher.data.productivity.toOccurrence
import app.still.launcher.schedule.Occurrence
import app.still.launcher.schedule.occurrencesBetween
import java.time.LocalDateTime

/** One notification to show: an occurrence with a time of day that isn't done yet. */
data class Reminder(
    val itemType: ItemType,
    val itemId: Long,
    val title: String,
    val occurrence: Occurrence,
    val at: LocalDateTime,
    /** Whether the notification can offer "Done": tasks and done-or-not habits. */
    val canMarkDone: Boolean,
) {
    /** Identifies the occurrence, e.g. for notification ids. */
    val key: String get() = "${itemType.name.lowercase()}/$itemId/${occurrence.date}/${occurrence.time}"
}

/**
 * Reminders due after [after] and up to [until] (inclusive), in time order. Only occurrences
 * with a time slot remind; anything already done or skipped is left out. [records] must cover
 * the dates in between.
 */
fun remindersBetween(
    after: LocalDateTime,
    until: LocalDateTime,
    tasks: List<Task>,
    habits: List<Habit>,
    records: List<OccurrenceRecord>,
): List<Reminder> = reminderCandidates(after, until, tasks, habits, records, includeHandled = false)

/**
 * Reminders that have gone off and still want attention, for the home screen: the most recent
 * one of each item within [lookbackDays], unless it's done, skipped or in [dismissed] (keys of
 * cards swiped away). An older occurrence is replaced once a newer one goes off, even if the
 * newer one is already done, so a repeating item never stacks up cards.
 */
fun activeReminders(
    now: LocalDateTime,
    tasks: List<Task>,
    habits: List<Habit>,
    records: List<OccurrenceRecord>,
    dismissed: Set<String>,
    lookbackDays: Long = ACTIVE_REMINDER_LOOKBACK_DAYS,
): List<Reminder> {
    val after = now.minusDays(lookbackDays)
    val pending = remindersBetween(after, now, tasks, habits, records).map { it.key }.toSet()
    return reminderCandidates(after, now, tasks, habits, records, includeHandled = true)
        .groupBy { it.itemType to it.itemId }
        .values
        .map { fired -> fired.maxBy { it.at } }
        .filter { it.key in pending && it.key !in dismissed }
        .sortedWith(compareBy({ it.at }, { it.title }))
}

private fun reminderCandidates(
    after: LocalDateTime,
    until: LocalDateTime,
    tasks: List<Task>,
    habits: List<Habit>,
    records: List<OccurrenceRecord>,
    includeHandled: Boolean,
): List<Reminder> {
    if (until <= after) return emptyList()
    val recordsByItem = records.groupBy { it.itemType to it.itemId }

    val taskReminders = tasks
        .filter { !it.archived && it.completedAt == null }
        .flatMap { task ->
            val schedule = task.schedule ?: return@flatMap emptyList()
            val handled = recordsByItem[ItemType.TASK to task.id].orEmpty()
                .filter { it.value >= 1.0 || it.skipped }
                .map { it.toOccurrence() }
                .toSet()
            schedule.occurrencesBetween(after.toLocalDate(), until.toLocalDate())
                .filter { includeHandled || it !in handled }
                .mapNotNull { occurrence ->
                    occurrence.time?.let { time ->
                        Reminder(ItemType.TASK, task.id, task.title, occurrence, occurrence.date.atTime(time), canMarkDone = true)
                    }
                }
        }

    val habitReminders = habits
        .filterNot { it.archived }
        .flatMap { habit ->
            val schedule = habit.schedule ?: return@flatMap emptyList()
            val handled = recordsByItem[ItemType.HABIT to habit.id].orEmpty()
                .filter { it.value >= habit.target || it.skipped }
                .map { it.toOccurrence() }
                .toSet()
            schedule.occurrencesBetween(after.toLocalDate(), until.toLocalDate())
                .filter { includeHandled || it !in handled }
                .mapNotNull { occurrence ->
                    occurrence.time?.let { time ->
                        Reminder(
                            ItemType.HABIT, habit.id, habit.title, occurrence, occurrence.date.atTime(time),
                            canMarkDone = habit.measure == HabitMeasure.CHECK,
                        )
                    }
                }
        }

    return (taskReminders + habitReminders)
        .filter { it.at > after && it.at <= until }
        .sortedWith(compareBy({ it.at }, { it.title }))
}

/** When the next reminder after [now] is due, looking a week ahead; null if there's none. */
fun nextReminderAt(
    now: LocalDateTime,
    tasks: List<Task>,
    habits: List<Habit>,
    records: List<OccurrenceRecord>,
): LocalDateTime? = remindersBetween(now, now.plusDays(REMINDER_LOOKAHEAD_DAYS), tasks, habits, records).firstOrNull()?.at

/** How far ahead the next reminder is searched; the alarm is re-armed every time one fires. */
const val REMINDER_LOOKAHEAD_DAYS = 8L

/** How long a reminder that went off can stay on the home screen without being dealt with. */
const val ACTIVE_REMINDER_LOOKBACK_DAYS = 7L
