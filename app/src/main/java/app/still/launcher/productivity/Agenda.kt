package app.still.launcher.productivity

import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.Plan
import app.still.launcher.data.productivity.PlanColor
import app.still.launcher.data.productivity.Task
import app.still.launcher.data.productivity.toOccurrence
import app.still.launcher.schedule.Occurrence
import app.still.launcher.schedule.isBehindPace
import app.still.launcher.schedule.occurrencesBetween
import app.still.launcher.schedule.occursOn
import app.still.launcher.schedule.pending
import app.still.launcher.schedule.rangeContaining
import java.time.LocalDate
import java.time.ZoneId

data class TaskEntry(
    val task: Task,
    /** Which occurrence this row is about; null for a task without a date. */
    val occurrence: Occurrence?,
    val isDone: Boolean,
    val isSkipped: Boolean = false,
    val isOverdue: Boolean = false,
    /** The task's subtasks, oldest first; ticked off right under it on the Today page. */
    val subtasks: List<Task> = emptyList(),
    /** Its plan and day, for a plan's task. */
    val plan: PlanPlace? = null,
) {
    val isPending: Boolean get() = !isDone && !isSkipped
}

data class HabitEntry(
    val habit: Habit,
    val occurrence: Occurrence,
    /** Count, minutes or amount logged so far; 1 for a ticked check. */
    val progress: Double,
    val isSkipped: Boolean,
    /** Days the target was met in the current quota period, for habits with a quota. */
    val quotaDone: Int?,
    /** Still needs doing today. A quota-only habit is pending only once it falls behind pace. */
    val isPending: Boolean,
) {
    val isDone: Boolean get() = progress >= habit.target
}

/** Everything the Today page shows for [date]. */
data class TodayAgenda(
    val date: LocalDate,
    val overdue: List<TaskEntry> = emptyList(),
    val today: List<TaskEntry> = emptyList(),
    val habits: List<HabitEntry> = emptyList(),
    val undated: List<TaskEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && today.isEmpty() && habits.isEmpty() && undated.isEmpty()

    /** Dated tasks and habits still to do today; undated tasks don't count. */
    val pendingCount: Int get() =
        overdue.count { it.isPending } + today.count { it.isPending } + habits.count { it.isPending }

    /** Tasks due today or overdue and not done, as shown on the home screen. */
    val dueTaskCount: Int get() = overdue.count { it.isPending } + today.count { it.isPending }

    val overdueTaskCount: Int get() = overdue.count { it.isPending }
}

/**
 * Builds the Today page from everything stored. [records] must cover at least the past year,
 * so recurring tasks can find their last occurrence and quotas can count their period.
 * Subtasks are left out; they belong to their parent task's details. Plan tasks are on the day
 * they've moved to (see [withPlanDates]), so a missed plan day is due today, not overdue, and a
 * paused plan's tasks wait with it; [plans] gives each plan's settings, name and colour.
 */
fun buildAgenda(
    date: LocalDate,
    stored: List<Task>,
    habits: List<Habit>,
    records: List<OccurrenceRecord>,
    zone: ZoneId = ZoneId.systemDefault(),
    plans: List<Plan> = emptyList(),
): TodayAgenda {
    val tasks = stored.withPlanDates(plans, date, zone)
    val plansById = plans.associateBy { it.id }
    val planDays = tasks.filter { it.planId != null && it.planDay != null }.groupBy { it.planId }.mapValues { (_, days) -> days.maxOf { it.planDay!! } }
    val recordsByItem = records.groupBy { it.itemType to it.itemId }
    val subtasksByParent = tasks
        .filter { it.parentId != null && !it.archived }
        .sortedBy { it.createdAt }
        .groupBy { it.parentId }

    val taskEntries = tasks
        .filter { it.parentId == null && !it.archived }
        .flatMap { task ->
            val subtasks = subtasksByParent[task.id].orEmpty()
            val plan = task.planId?.let { id ->
                task.planDay?.let { day ->
                    val stored = plansById[id]
                    PlanPlace(stored?.name.orEmpty(), day, planDays.getValue(id), stored?.color ?: PlanColor.TEAL)
                }
            }
            taskEntries(task, date, recordsByItem[ItemType.TASK to task.id].orEmpty(), zone).map { it.copy(subtasks = subtasks, plan = plan) }
        }

    val habitEntries = habits
        .filterNot { it.archived }
        .flatMap { habitEntries(it, date, recordsByItem[ItemType.HABIT to it.id].orEmpty()) }

    return TodayAgenda(
        date = date,
        overdue = taskEntries.filter { it.isOverdue }.sortedWith(taskOrder),
        today = taskEntries.filter { !it.isOverdue && it.occurrence != null }.sortedWith(taskOrder),
        habits = habitEntries.sortedWith(habitOrder),
        undated = taskEntries.filter { it.occurrence == null }.sortedWith(taskOrder),
    )
}

private fun taskEntries(task: Task, date: LocalDate, records: List<OccurrenceRecord>, zone: ZoneId): List<TaskEntry> {
    val schedule = task.schedule
    val completedToday = task.completedAt?.atZone(zone)?.toLocalDate() == date

    // Undated: shown until done, and for the rest of the day it was ticked off.
    if (schedule == null) {
        val visible = task.completedAt == null || completedToday
        return if (visible) listOf(TaskEntry(task, null, isDone = task.completedAt != null)) else emptyList()
    }

    // One-time: the completion lives on the task.
    if (!schedule.isRecurring) {
        val occurrence = Occurrence(schedule.start, schedule.times.minOrNull())
        return when {
            task.completedAt != null ->
                if (completedToday) listOf(TaskEntry(task, occurrence, isDone = true)) else emptyList()
            schedule.pending(date, emptySet(), task.missedPolicy).isNotEmpty() ->
                listOf(TaskEntry(task, occurrence, isDone = false, isOverdue = schedule.start < date))
            else -> emptyList()
        }
    }

    // Recurring: each occurrence has its own record.
    val handled = records.filter { it.value >= 1.0 || it.skipped }.map { it.toOccurrence() }.toSet()
    val pending = schedule.pending(date, handled, task.missedPolicy)
        .map { TaskEntry(task, it, isDone = false, isOverdue = it.date < date) }
    val handledToday = records
        .filter { it.date == date && (it.value >= 1.0 || it.skipped) }
        .map { TaskEntry(task, it.toOccurrence(), isDone = it.value >= 1.0 && !it.skipped, isSkipped = it.skipped) }
    return pending + handledToday
}

private fun habitEntries(habit: Habit, date: LocalDate, records: List<OccurrenceRecord>): List<HabitEntry> {
    val quotaDone = habit.quota?.let { quota ->
        val period = quota.period.rangeContaining(date)
        records.filter { it.date in period && !it.skipped && it.value >= habit.target }.map { it.date }.distinct().size
    }
    val occurrences = when {
        habit.schedule != null ->
            if (habit.schedule.occursOn(date)) habit.schedule.occurrencesBetween(date, date) else emptyList()
        // Quota-only habits can be logged on any day.
        else -> listOf(Occurrence(date))
    }
    return occurrences.map { occurrence ->
        val record = records.firstOrNull { it.toOccurrence() == occurrence }
        val progress = record?.value ?: 0.0
        val skipped = record?.skipped == true
        val due = habit.schedule != null || habit.quota?.isBehindPace(quotaDone ?: 0, date) == true
        HabitEntry(
            habit = habit,
            occurrence = occurrence,
            progress = progress,
            isSkipped = skipped,
            quotaDone = quotaDone,
            isPending = due && !skipped && progress < habit.target,
        )
    }
}

// Unfinished first, then higher priority, then earlier, then older.
private val taskOrder = compareBy<TaskEntry>(
    { !it.isPending },
    { -it.task.priority.ordinal },
    { it.occurrence?.date },
    { it.occurrence?.time },
    { it.task.createdAt },
)

// Pending first, then anything not finished, then done; within each, priority and time.
private val habitOrder = compareBy<HabitEntry>(
    { !it.isPending },
    { it.isDone || it.isSkipped },
    { -it.habit.priority.ordinal },
    { it.occurrence.time },
    { it.habit.createdAt },
)
