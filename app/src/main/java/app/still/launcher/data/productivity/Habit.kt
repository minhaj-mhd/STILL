package app.still.launcher.data.productivity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.Schedule
import java.time.Instant

/** What a habit tracks. */
enum class HabitMeasure {
    /** Done or not done. */
    CHECK,

    /** A number of times, e.g. 8 glasses of water. */
    COUNT,

    /** Minutes, e.g. read for 15 minutes. Can be completed by a STILL timer. */
    DURATION,

    /** An amount in [Habit.unit], e.g. 5 km. */
    MEASUREMENT,
}

/** A habit is due on its [schedule], or counted against its [quota]; it has at least one of the two. */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val priority: Priority = Priority.NONE,
    val measure: HabitMeasure = HabitMeasure.CHECK,
    /** What counts as done for one occurrence: 1 for a check, otherwise a count, minutes or amount. */
    val target: Double = 1.0,
    /** Shown after amounts of a [HabitMeasure.MEASUREMENT] habit, e.g. "km". */
    val unit: String? = null,
    val schedule: Schedule? = null,
    val quota: Quota? = null,
    val createdAt: Instant = Instant.now(),
    val archived: Boolean = false,
) {
    init { require(schedule != null || quota != null) { "A habit needs a schedule or a quota" } }
}
