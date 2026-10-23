package app.still.launcher.data.blocking

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import app.still.launcher.schedule.Schedule
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Blocks [packages] every day [schedule] occurs, from [startMinute] to [endMinute] (minutes past
 * midnight). An end at or before the start runs into the next morning, e.g. 21:00–07:00.
 * Days turned off "just for today" are the schedule's skipped dates.
 */
@Entity(tableName = "blocking_schedules")
data class BlockingSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val schedule: Schedule,
    val startMinute: Int,
    val endMinute: Int,
    val packages: Set<String>,
    val enabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
)

@Dao
interface BlockingScheduleDao {

    @Query("SELECT * FROM blocking_schedules ORDER BY createdAt")
    fun observeSchedules(): Flow<List<BlockingSchedule>>

    @Query("SELECT * FROM blocking_schedules WHERE id = :scheduleId")
    suspend fun getSchedule(scheduleId: Long): BlockingSchedule?

    @Insert
    suspend fun insert(schedule: BlockingSchedule): Long

    @Update
    suspend fun update(schedule: BlockingSchedule)

    @Query("DELETE FROM blocking_schedules WHERE id = :scheduleId")
    suspend fun delete(scheduleId: Long)
}
