package app.still.launcher.data.productivity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.still.launcher.schedule.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** The colour a plan is marked with, on the Plans page and beside its tasks on Today. */
enum class PlanColor { TEAL, VIOLET, CORAL, AMBER }

/** The plan was paused from [from] until it carried on, on [until]; [until] is null while it's paused. */
@Serializable
data class PlanPause(
    @Serializable(with = LocalDateSerializer::class) val from: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val until: LocalDate? = null,
) {
    operator fun contains(date: LocalDate): Boolean = date >= from && (until == null || date < until)
}

/**
 * A plan: tasks laid out over numbered days, written in STILL or pasted. Its tasks carry its id and
 * their day ([Task.planId], [Task.planDay]); where each day falls is worked out from the plan (see
 * productivity/Plans.kt).
 */
@Entity(tableName = "plans")
data class Plan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Empty when it has none. */
    val name: String,
    val createdAt: Instant = Instant.now(),
    /** Day 1's date. Null on plans added before 29 October 2026; theirs is worked out from their tasks. */
    val start: LocalDate? = null,
    /** The weekdays plan days fall on, Monday as the lowest bit. */
    @ColumnInfo(defaultValue = "127") val weekdays: Int = EVERY_DAY,
    /** A missed day stays due and moves the rest along; otherwise each day keeps its date and goes overdue. */
    @ColumnInfo(defaultValue = "1") val movesAlong: Boolean = true,
    @ColumnInfo(defaultValue = "TEAL") val color: PlanColor = PlanColor.TEAL,
    /** Oldest first; the last has no end while the plan is paused. */
    @ColumnInfo(defaultValue = "[]") val pauses: List<PlanPause> = emptyList(),
) {
    val isPaused: Boolean get() = pauses.lastOrNull()?.until == null && pauses.isNotEmpty()

    fun fallsOn(day: DayOfWeek): Boolean = weekdays and (1 shl day.ordinal) != 0

    companion object {
        const val EVERY_DAY = 0b1111111
        const val WEEKDAYS = 0b0011111

        fun weekdaysOf(days: Set<DayOfWeek>): Int = days.fold(0) { bits, day -> bits or (1 shl day.ordinal) }
    }
}
