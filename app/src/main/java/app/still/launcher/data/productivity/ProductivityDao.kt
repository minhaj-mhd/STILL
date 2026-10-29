package app.still.launcher.data.productivity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
abstract class ProductivityDao {

    @Query("SELECT * FROM tasks WHERE archived = 0 ORDER BY createdAt")
    abstract fun observeTasks(): Flow<List<Task>>

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY createdAt")
    abstract fun observeHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM plans")
    abstract fun observePlans(): Flow<List<Plan>>

    @Query("SELECT * FROM plans")
    abstract suspend fun getPlans(): List<Plan>

    @Query("SELECT * FROM plans WHERE id = :planId")
    abstract suspend fun getPlan(planId: Long): Plan?

    @Query("SELECT * FROM tasks WHERE planId = :planId AND parentId IS NULL ORDER BY createdAt")
    abstract suspend fun getPlanTasks(planId: Long): List<Task>

    @Update
    abstract suspend fun updatePlan(plan: Plan)

    @Query("SELECT * FROM occurrence_records WHERE date BETWEEN :from AND :to")
    abstract fun observeRecords(from: LocalDate, to: LocalDate): Flow<List<OccurrenceRecord>>

    @Query("SELECT * FROM occurrence_records WHERE itemType = :type AND itemId = :itemId")
    abstract fun observeRecordsFor(type: ItemType, itemId: Long): Flow<List<OccurrenceRecord>>

    /** Changes whenever any record is added, changed or removed. */
    @Query("SELECT COUNT(*) || ':' || IFNULL(MAX(updatedAt), 0) FROM occurrence_records")
    abstract fun observeRecordsVersion(): Flow<String>

    @Query("SELECT `key` FROM dismissed_reminders")
    abstract fun observeDismissedReminderKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertDismissedReminder(dismissed: DismissedReminder)

    @Query("DELETE FROM dismissed_reminders WHERE date < :before")
    abstract suspend fun deleteDismissedRemindersBefore(before: LocalDate)

    @Query("SELECT * FROM tasks WHERE archived = 0")
    abstract suspend fun getActiveTasks(): List<Task>

    @Query("SELECT * FROM habits WHERE archived = 0")
    abstract suspend fun getActiveHabits(): List<Habit>

    @Query("SELECT * FROM occurrence_records WHERE date BETWEEN :from AND :to")
    abstract suspend fun getRecords(from: LocalDate, to: LocalDate): List<OccurrenceRecord>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    abstract suspend fun getTask(taskId: Long): Task?

    @Query("SELECT * FROM tasks WHERE parentId = :parentId ORDER BY createdAt")
    abstract suspend fun getSubtasks(parentId: Long): List<Task>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    abstract suspend fun getHabit(habitId: Long): Habit?

    @Insert
    abstract suspend fun insertTask(task: Task): Long

    @Update
    abstract suspend fun updateTask(task: Task)

    @Insert
    abstract suspend fun insertHabit(habit: Habit): Long

    @Update
    abstract suspend fun updateHabit(habit: Habit)

    @Upsert
    abstract suspend fun upsertRecord(record: OccurrenceRecord)

    @Query(
        "SELECT * FROM occurrence_records " +
            "WHERE itemType = :type AND itemId = :itemId AND date = :date AND slotMinute = :slotMinute",
    )
    abstract suspend fun getRecord(type: ItemType, itemId: Long, date: LocalDate, slotMinute: Int): OccurrenceRecord?

    @Query(
        "DELETE FROM occurrence_records " +
            "WHERE itemType = :type AND itemId = :itemId AND date = :date AND slotMinute = :slotMinute",
    )
    abstract suspend fun deleteRecord(type: ItemType, itemId: Long, date: LocalDate, slotMinute: Int)

    /**
     * Inserts or updates a task together with its subtasks, all or nothing.
     * Subtasks with id 0 are new; [removedSubtaskIds] are deleted with their history.
     * Returns the task's id.
     */
    @Transaction
    open suspend fun saveTaskWithSubtasks(task: Task, subtasks: List<Task>, removedSubtaskIds: List<Long>): Long {
        val taskId = if (task.id == 0L) insertTask(task) else task.id.also { updateTask(task) }
        removedSubtaskIds.forEach { deleteTaskTree(it) }
        subtasks.forEach { subtask ->
            val child = subtask.copy(parentId = taskId)
            if (child.id == 0L) insertTask(child) else updateTask(child)
        }
        return taskId
    }

    /** Adds [plan] with its tasks and their subtasks, all or nothing. Returns the plan's id. */
    @Transaction
    open suspend fun insertPlanWithTasks(plan: Plan, tasks: List<Pair<Task, List<Task>>>): Long {
        val planId = insertPlan(plan)
        tasks.forEach { (task, subtasks) -> saveTaskWithSubtasks(task.copy(planId = planId), subtasks, removedSubtaskIds = emptyList()) }
        return planId
    }

    /**
     * Saves an edited plan, all or nothing: [tasks] with id 0 are new, the rest are updated, and
     * [removedTaskIds] are deleted with their subtasks and history. A plan left with no tasks goes too.
     */
    @Transaction
    open suspend fun savePlan(plan: Plan, tasks: List<Task>, removedTaskIds: List<Long>) {
        updatePlan(plan)
        tasks.forEach { task ->
            val joined = task.copy(planId = plan.id)
            if (joined.id == 0L) insertTask(joined) else updateTask(joined)
        }
        removedTaskIds.forEach { deleteTaskTree(it) }
    }

    /** Deletes a plan with all its tasks, their subtasks and history. */
    @Transaction
    open suspend fun deletePlanWithTasks(planId: Long) {
        getPlanTasks(planId).forEach { deleteTaskTree(it.id) }
        deletePlan(planId)
    }

    /** Deletes a task, its subtasks, and their history, and its plan once the plan has no tasks left. */
    @Transaction
    open suspend fun deleteTaskTree(taskId: Long) {
        subtaskIds(taskId).forEach { deleteTaskTree(it) }
        deleteRecordsFor(ItemType.TASK, taskId)
        deleteTask(taskId)
        deleteEmptyPlans()
    }

    /** Deletes a habit and its history. */
    @Transaction
    open suspend fun deleteHabitWithHistory(habitId: Long) {
        deleteRecordsFor(ItemType.HABIT, habitId)
        deleteHabit(habitId)
    }

    @Query("SELECT id FROM tasks WHERE parentId = :parentId")
    protected abstract suspend fun subtaskIds(parentId: Long): List<Long>

    @Query("DELETE FROM tasks WHERE id = :taskId")
    protected abstract suspend fun deleteTask(taskId: Long)

    @Query("DELETE FROM habits WHERE id = :habitId")
    protected abstract suspend fun deleteHabit(habitId: Long)

    @Insert
    protected abstract suspend fun insertPlan(plan: Plan): Long

    @Query("DELETE FROM plans WHERE id = :planId")
    protected abstract suspend fun deletePlan(planId: Long)

    @Query("DELETE FROM plans WHERE id NOT IN (SELECT planId FROM tasks WHERE planId IS NOT NULL)")
    protected abstract suspend fun deleteEmptyPlans()

    @Query("DELETE FROM occurrence_records WHERE itemType = :type AND itemId = :itemId")
    protected abstract suspend fun deleteRecordsFor(type: ItemType, itemId: Long)
}
