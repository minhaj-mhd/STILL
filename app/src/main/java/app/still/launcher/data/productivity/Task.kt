package app.still.launcher.data.productivity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.still.launcher.schedule.MissedPolicy
import app.still.launcher.schedule.Schedule
import java.time.Instant
import java.time.LocalDate

/** How much a task or habit matters. Task gates can block apps by priority (see docs/task-gated-blocking.md). */
enum class Priority { NONE, LOW, MEDIUM, HIGH }

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("parentId")],
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val priority: Priority = Priority.NONE,
    /** Set on subtasks. Subtasks never gate apps on their own. */
    val parentId: Long? = null,
    /** When it's due. Null for a task without a date. */
    val schedule: Schedule? = null,
    val missedPolicy: MissedPolicy = MissedPolicy.CARRY_OVER,
    /** When a one-time or undated task was done. Recurring tasks record each occurrence in [OccurrenceRecord]. */
    val completedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val archived: Boolean = false,
    /** Set on the tasks of a [Plan]. */
    val planId: Long? = null,
    /** Which day of its plan, from 1. [schedule] holds the date it was planned for; where it's due now comes from the plan. */
    val planDay: Int? = null,
    /**
     * The day a plan task was skipped. In a plan that moves along it waits until the next day; in
     * one that keeps its dates it's let go, neither done nor overdue.
     */
    val skippedOn: LocalDate? = null,
)
