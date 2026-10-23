package app.still.launcher.productivity

import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.Task
import app.still.launcher.schedule.Occurrence
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.QuotaPeriod
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodayProgressTest {

    private val today = LocalDate.parse("2026-10-23")

    private fun task(id: Long, done: Boolean = false, overdue: Boolean = false, skipped: Boolean = false) = TaskEntry(
        task = Task(id = id, title = "Task $id", schedule = Schedule(today)),
        occurrence = Occurrence(if (overdue) today.minusDays(1) else today),
        isDone = done,
        isSkipped = skipped,
        isOverdue = overdue,
    )

    private fun habit(id: Long, done: Boolean = false, pending: Boolean = !done, quotaOnly: Boolean = false) = HabitEntry(
        habit = if (quotaOnly) Habit(id = id, title = "Habit $id", quota = Quota(3, QuotaPeriod.WEEK))
        else Habit(id = id, title = "Habit $id", schedule = Schedule(today, Recurrence.Daily())),
        occurrence = Occurrence(today),
        progress = if (done) 1.0 else 0.0,
        isSkipped = false,
        quotaDone = null,
        isPending = pending,
    )

    @Test
    fun `done comes first, then overdue, then what's still to do`() {
        val agenda = TodayAgenda(
            date = today,
            overdue = listOf(task(1, overdue = true)),
            today = listOf(task(2), task(3, done = true)),
            habits = listOf(habit(4, done = true), habit(5)),
        )
        assertEquals(
            listOf(ItemProgress.DONE, ItemProgress.DONE, ItemProgress.OVERDUE, ItemProgress.PENDING, ItemProgress.PENDING),
            agenda.progress,
        )
    }

    @Test
    fun `skipped items, undated tasks and habits not due today are left out`() {
        val agenda = TodayAgenda(
            date = today,
            today = listOf(task(1, skipped = true), task(2)),
            habits = listOf(habit(3, pending = false, quotaOnly = true)),
            undated = listOf(TaskEntry(Task(id = 9, title = "Someday"), occurrence = null, isDone = false)),
        )
        assertEquals(listOf(ItemProgress.PENDING), agenda.progress)
    }
}
