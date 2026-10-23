package app.still.launcher.data

import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.ProductivityDao
import app.still.launcher.data.productivity.Task
import app.still.launcher.schedule.Occurrence
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Tasks, habits and what happened to each occurrence. */
class ProductivityRepository(private val dao: ProductivityDao) {

    val tasks: Flow<List<Task>> = dao.observeTasks()

    val habits: Flow<List<Habit>> = dao.observeHabits()

    /** Occurrence records from [from] to [to], both inclusive. */
    fun records(from: LocalDate, to: LocalDate): Flow<List<OccurrenceRecord>> = dao.observeRecords(from, to)

    suspend fun addTask(task: Task): Long = dao.insertTask(task)

    suspend fun updateTask(task: Task) = dao.updateTask(task)

    /** Also deletes its subtasks and history. */
    suspend fun deleteTask(taskId: Long) = dao.deleteTaskTree(taskId)

    suspend fun addHabit(habit: Habit): Long = dao.insertHabit(habit)

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
            dao.updateTask(task.copy(completedAt = if (done) Instant.now() else null))
        }
    }

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
