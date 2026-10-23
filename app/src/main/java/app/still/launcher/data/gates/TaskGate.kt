@file:UseSerializers(DayOfWeekSerializer::class)

package app.still.launcher.data.gates

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.Priority
import app.still.launcher.schedule.DayOfWeekSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.Instant

/** What keeps a gate closed: pending items of a priority, or particular tasks and habits. */
enum class GateCondition { PRIORITY, ITEMS }

/** A task or habit chosen for a gate. */
@Serializable
data class GateItem(val type: ItemType, val id: Long)

/**
 * When a gate applies: on [days], from [startMinute] to [endMinute] (minutes past midnight). An
 * end at or before the start runs into the next morning.
 */
@Serializable
data class GateWindow(val days: Set<DayOfWeek>, val startMinute: Int, val endMinute: Int)

/**
 * A task gate keeps [packages] closed while tasks or habits are still to do (see
 * docs/task-gated-blocking.md). Outside its [window] the apps open freely.
 */
@Entity(tableName = "task_gates")
data class TaskGate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packages: Set<String>,
    val condition: GateCondition = GateCondition.PRIORITY,
    /** [GateCondition.PRIORITY]: pending items at this priority or higher keep the gate closed. */
    val minPriority: Priority = Priority.HIGH,
    val includeTasks: Boolean = true,
    val includeHabits: Boolean = true,
    /** [GateCondition.PRIORITY]: tasks without a date count too, so they can gate until done. */
    val includeUndated: Boolean = false,
    /** [GateCondition.ITEMS]: the chosen tasks and habits. */
    val items: List<GateItem> = emptyList(),
    /** [GateCondition.ITEMS]: all of them must be done, or any one of them is enough. */
    val requireAll: Boolean = true,
    /** Null applies all the time. */
    val window: GateWindow? = null,
    val emergencyOpensPerDay: Int = DEFAULT_EMERGENCY_OPENS,
    val emergencyDelaySeconds: Int = DEFAULT_EMERGENCY_DELAY_SECONDS,
    val emergencyMinutes: Int = DEFAULT_EMERGENCY_MINUTES,
    val enabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        const val DEFAULT_EMERGENCY_OPENS = 3
        const val DEFAULT_EMERGENCY_DELAY_SECONDS = 30
        const val DEFAULT_EMERGENCY_MINUTES = 5
    }
}

enum class GateEventType {
    /** An app was opened past a closed gate; [GateEvent.openUntil] says for how long. */
    EMERGENCY_OPEN,
}

/** Something that happened at a gate; kept for emergency-open limits and, later, Insights. */
@Entity(tableName = "gate_events", indices = [Index("time")])
data class GateEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Instant,
    val gateId: Long,
    val packageName: String,
    val type: GateEventType,
    val openUntil: Instant? = null,
)

@Dao
interface TaskGateDao {

    @Query("SELECT * FROM task_gates ORDER BY createdAt")
    fun observeGates(): Flow<List<TaskGate>>

    @Query("SELECT * FROM task_gates WHERE id = :gateId")
    suspend fun getGate(gateId: Long): TaskGate?

    @Insert
    suspend fun insert(gate: TaskGate): Long

    @Update
    suspend fun update(gate: TaskGate)

    @Query("DELETE FROM task_gates WHERE id = :gateId")
    suspend fun delete(gateId: Long)

    @Query("SELECT * FROM gate_events WHERE time >= :since ORDER BY time")
    fun observeEventsSince(since: Instant): Flow<List<GateEvent>>

    @Insert
    suspend fun insertEvents(events: List<GateEvent>)
}
