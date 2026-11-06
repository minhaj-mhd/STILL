package app.still.launcher.data.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** A finished day of phone use (see [app.still.launcher.history.DayUsage]). */
@Entity(tableName = "usage_days")
data class UsageDayRecord(
    @PrimaryKey val day: LocalDate,
    /** The hour the day started at when it was saved: 0 for midnight, 4 for 4 AM. */
    val dayStartHour: Int,
    /** Every app but STILL. */
    val screenMillis: Long,
    val stillMillis: Long,
    val opens: Int,
    val unlocks: Int,
    /** Time in apps in each clock hour, 0 to 23. */
    val hourMillis: List<Long>,
    val savedAt: Instant = Instant.now(),
    /** What the daily limit counted that day, and the limit then; 0 when it was off. */
    @ColumnInfo(defaultValue = "0") val countedMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val limitMillis: Long = 0,
) {
    val isOverLimit: Boolean get() = limitMillis > 0 && countedMillis >= limitMillis
}

/** One app's time and opens on a saved day. */
@Entity(tableName = "usage_day_apps", primaryKeys = ["day", "packageName"], indices = [Index("packageName")])
data class UsageAppRecord(
    val day: LocalDate,
    val packageName: String,
    val millis: Long,
    val opens: Int,
)

/** What STILL put in front of an app. */
enum class InterventionType {
    /** Focus mode keeps it closed. */
    FOCUS,

    /** Blocked for a while from its menu. */
    BLOCKED,

    /** Blocked by a blocking schedule. */
    SCHEDULE,

    /** A task gate is waiting on tasks or habits. */
    GATE,

    /** The day's phone limit is used up. */
    DAILY_LIMIT,

    /** Its own limits keep it closed: done for today, or waiting between visits. */
    APP_LIMIT,

    /** Its visit ran out while it was open. */
    VISIT_UP,

    /** "What are you opening it for?" */
    PAUSE,

    LAUNCH_DELAY,

    /** An in-app time reminder ran out. */
    TIME_UP,

    /** A Shorts or Reels player or page was left. */
    SHORT_VIDEO,
}

/** One time STILL stepped in front of an app. What was answered at a pause is in the choices table. */
@Entity(tableName = "interventions", indices = [Index("time")])
data class InterventionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Instant,
    val packageName: String,
    val type: InterventionType,
)

/** A focus mode session that has ended. */
@Entity(tableName = "focus_mode_periods")
data class FocusModePeriod(
    @PrimaryKey val startedAt: Instant,
    val endedAt: Instant,
)

@Dao
interface HistoryDao {

    @Query("SELECT MAX(day) FROM usage_days")
    suspend fun lastDay(): LocalDate?

    @Query("SELECT MIN(day) FROM usage_days")
    fun observeFirstDay(): Flow<LocalDate?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(day: UsageDayRecord)

    @Query("DELETE FROM usage_day_apps WHERE day = :day")
    suspend fun deleteApps(day: LocalDate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<UsageAppRecord>)

    @Transaction
    suspend fun saveDay(day: UsageDayRecord, apps: List<UsageAppRecord>) {
        insertDay(day)
        deleteApps(day.day)
        insertApps(apps)
    }

    @Query("SELECT * FROM usage_days WHERE day BETWEEN :from AND :to ORDER BY day")
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<UsageDayRecord>>

    @Query("SELECT * FROM usage_day_apps WHERE day BETWEEN :from AND :to")
    fun observeApps(from: LocalDate, to: LocalDate): Flow<List<UsageAppRecord>>

    @Insert
    suspend fun insertIntervention(record: InterventionRecord)

    @Query("SELECT * FROM interventions WHERE time >= :from AND time < :to ORDER BY time")
    fun observeInterventions(from: Instant, to: Instant): Flow<List<InterventionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusPeriod(period: FocusModePeriod)

    @Query("SELECT * FROM focus_mode_periods WHERE endedAt > :from AND startedAt < :to ORDER BY startedAt")
    fun observeFocusPeriods(from: Instant, to: Instant): Flow<List<FocusModePeriod>>
}
