package app.still.launcher.data.productivity

import androidx.room.Dao
import androidx.room.Insert
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

    @Query("SELECT * FROM occurrence_records WHERE date BETWEEN :from AND :to")
    abstract fun observeRecords(from: LocalDate, to: LocalDate): Flow<List<OccurrenceRecord>>

    @Query("SELECT * FROM occurrence_records WHERE itemType = :type AND itemId = :itemId")
    abstract fun observeRecordsFor(type: ItemType, itemId: Long): Flow<List<OccurrenceRecord>>

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

    /** Deletes a task, its subtasks, and their history. */
    @Transaction
    open suspend fun deleteTaskTree(taskId: Long) {
        subtaskIds(taskId).forEach { deleteTaskTree(it) }
        deleteRecordsFor(ItemType.TASK, taskId)
        deleteTask(taskId)
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

    @Query("DELETE FROM occurrence_records WHERE itemType = :type AND itemId = :itemId")
    protected abstract suspend fun deleteRecordsFor(type: ItemType, itemId: Long)
}
