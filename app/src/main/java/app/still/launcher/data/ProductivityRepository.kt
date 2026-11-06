package app.still.launcher.data

import app.still.launcher.data.productivity.DismissedReminder
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.Plan
import app.still.launcher.data.productivity.ProductivityDao
import app.still.launcher.data.productivity.Task
import app.still.launcher.schedule.Occurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Tasks, habits and what happened to each occurrence. */
class ProductivityRepository(private val dao: ProductivityDao) {

    val tasks: Flow<List<Task>> = dao.observeTasks()

    val habits: Flow<List<Habit>> = dao.observeHabits()

    /** Occurrence records from [from] to [to], both inclusive. */
    fun records(from: LocalDate, to: LocalDate): Flow<List<OccurrenceRecord>> = dao.observeRecords(from, to)

    /** Emits whenever any task, habit, record or plan changes. */
    val changes: Flow<Unit> = combine(dao.observeTasks(), dao.observeHabits(), dao.observeRecordsVersion(), dao.observePlans()) { _, _, _, _ -> }

    /** Keys of reminder cards swiped off the home screen. */
    val dismissedReminderKeys: Flow<Set<String>> = dao.observeDismissedReminderKeys().map { it.toSet() }

    suspend fun dismissReminder(key: String, date: LocalDate) = dao.insertDismissedReminder(DismissedReminder(key, date))

    /** Forgets dismissals for occurrences before [date]; they can't come back to home anyway. */
    suspend fun forgetDismissedRemindersBefore(date: LocalDate) = dao.deleteDismissedRemindersBefore(date)

    suspend fun getActiveTasks(): List<Task> = dao.getActiveTasks()

    suspend fun getActiveHabits(): List<Habit> = dao.getActiveHabits()

    suspend fun getRecords(from: LocalDate, to: LocalDate): List<OccurrenceRecord> = dao.getRecords(from, to)

    /** Every record of one habit, for streaks and history. */
    fun habitRecords(habitId: Long): Flow<List<OccurrenceRecord>> = dao.observeRecordsFor(ItemType.HABIT, habitId)

    suspend fun getTask(taskId: Long): Task? = dao.getTask(taskId)

    suspend fun getSubtasks(taskId: Long): List<Task> = dao.getSubtasks(taskId)

    suspend fun getHabit(habitId: Long): Habit? = dao.getHabit(habitId)

    suspend fun addTask(task: Task): Long = dao.insertTask(task)

    /** Saves a new or edited task and its subtasks in one transaction; returns the task's id. */
    suspend fun saveTask(task: Task, subtasks: List<Task>, removedSubtaskIds: List<Long>): Long =
        dao.saveTaskWithSubtasks(task, subtasks, removedSubtaskIds)

    /** Saves a new or edited habit; returns its id. */
    suspend fun saveHabit(habit: Habit): Long =
        if (habit.id == 0L) dao.insertHabit(habit) else habit.id.also { dao.updateHabit(habit) }

    suspend fun updateTask(task: Task) = dao.updateTask(task)

    /** Also deletes its subtasks and history. */
    suspend fun deleteTask(taskId: Long) = dao.deleteTaskTree(taskId)

    suspend fun addHabit(habit: Habit): Long = dao.insertHabit(habit)

    /** Every plan, paused and finished ones too. */
    val plans: Flow<List<Plan>> = dao.observePlans()

    suspend fun getPlans(): List<Plan> = dao.getPlans()

    suspend fun getPlan(planId: Long): Plan? = dao.getPlan(planId)

    /** A plan's tasks, without subtasks, oldest first. */
    suspend fun getPlanTasks(planId: Long): List<Task> = dao.getPlanTasks(planId)

    /** Adds a new plan with its tasks and their subtasks in one go; returns its id. */
    suspend fun addPlan(plan: Plan, tasks: List<Pair<Task, List<Task>>>): Long = dao.insertPlanWithTasks(plan, tasks)

    /** Saves an edited plan and its tasks in one go; [removedTaskIds] are deleted with their history. */
    suspend fun savePlan(plan: Plan, tasks: List<Task>, removedTaskIds: List<Long>) = dao.savePlan(plan, tasks, removedTaskIds)

    /** Saves a plan's own settings, e.g. a pause. */
    suspend fun updatePlan(plan: Plan) = dao.updatePlan(plan)

    /** Skips day [day] of plan [planId] on [today], or takes the skip back: each of its tasks not done yet. */
    suspend fun setPlanDaySkipped(planId: Long, day: Int, skipped: Boolean, today: LocalDate) {
        dao.getPlanTasks(planId)
            .filter { it.planDay == day && it.parentId == null && it.completedAt == null }
            .forEach { dao.updateTask(it.copy(skippedOn = if (skipped) today else null)) }
    }

    /** Deletes a plan with all its tasks and their history. */
    suspend fun deletePlan(planId: Long) = dao.deletePlanWithTasks(planId)

    suspend fun updateHabit(habit: Habit) = dao.updateHabit(habit)

    /** Also deletes its history. */
    suspend fun deleteHabit(habitId: Long) = dao.deleteHabitWithHistory(habitId)

    /**
     * Marks a task done or not done. A recurring task records the given [occurrence];
     * a one-time or undated task stores the completion on the task itself.
     */
    suspend fun setTaskDone(task: Task, occurrence: Occurrence?, done: Boolean) {
        if (task.schedule?.isRecurring == true) {
            requireNotNull(occurrence) { "A recurring task needs the occurrence being completed" }
            setProgress(ItemType.TASK, task.id, occurrence, if (done) 1.0 else 0.0, viaTimer = false)
        } else {
            // The Today page's copy of a plan task carries the day it has moved to; keep the stored one.
            val stored = dao.getTask(task.id) ?: return
            dao.updateTask(stored.copy(completedAt = if (done) Instant.now() else null))
        }
    }

    /** What's logged for one occurrence of a habit so far; 0 when nothing is. */
    suspend fun getHabitProgress(habitId: Long, occurrence: Occurrence): Double =
        dao.getRecord(ItemType.HABIT, habitId, occurrence.date, OccurrenceRecord.slotMinuteOf(occurrence))?.value ?: 0.0

    /** Sets a habit's progress for one occurrence: 1 for a check, or the count, minutes or amount so far. */
    suspend fun setHabitProgress(habit: Habit, occurrence: Occurrence, value: Double, viaTimer: Boolean = false) {
        setProgress(ItemType.HABIT, habit.id, occurrence, value, viaTimer)
    }

    /** Excuses one occurrence ("skip today"): no longer pending, but counted as missed in streaks. */
    suspend fun setSkipped(type: ItemType, itemId: Long, occurrence: Occurrence, skipped: Boolean) {
        val existing = dao.getRecord(type, itemId, occurrence.date, OccurrenceRecord.slotMinuteOf(occurrence))
        val record = (existing ?: newRecord(type, itemId, occurrence)).copy(skipped = skipped, updatedAt = Instant.now())
        save(record)
    }

    private suspend fun setProgress(type: ItemType, itemId: Long, occurrence: Occurrence, value: Double, viaTimer: Boolean) {
        val existing = dao.getRecord(type, itemId, occurrence.date, OccurrenceRecord.slotMinuteOf(occurrence))
        val record = (existing ?: newRecord(type, itemId, occurrence))
            .copy(value = value.coerceAtLeast(0.0), viaTimer = viaTimer, updatedAt = Instant.now())
        save(record)
    }

    /** A record with no progress and no skip carries no information, so it's removed rather than kept. */
    private suspend fun save(record: OccurrenceRecord) {
        if (record.value == 0.0 && !record.skipped) {
            dao.deleteRecord(record.itemType, record.itemId, record.date, record.slotMinute)
        } else {
            dao.upsertRecord(record)
        }
    }

    private fun newRecord(type: ItemType, itemId: Long, occurrence: Occurrence) = OccurrenceRecord(
        itemType = type,
        itemId = itemId,
        date = occurrence.date,
        slotMinute = OccurrenceRecord.slotMinuteOf(occurrence),
    )
}
