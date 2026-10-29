package app.still.launcher.productivity

import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.Priority
import app.still.launcher.data.productivity.Task
import app.still.launcher.schedule.Occurrence
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimelineTest {

    private val today = LocalDate.parse("2026-10-26")

    private fun task(id: Long, time: String?, done: Boolean = false, priority: Priority = Priority.NONE, overdue: Boolean = false) = TaskEntry(
        task = Task(id = id, title = "Task $id", priority = priority, schedule = Schedule(today)),
        occurrence = Occurrence(if (overdue) today.minusDays(1) else today, time?.let(LocalTime::parse)),
        isDone = done,
        isOverdue = overdue,
    )

    private fun habit(id: Long, time: String?) = HabitEntry(
        habit = Habit(id = id, title = "Habit $id", schedule = Schedule(today, Recurrence.Daily())),
        occurrence = Occurrence(today, time?.let(LocalTime::parse)),
        progress = 0.0,
        isSkipped = false,
        quotaDone = null,
        isPending = true,
    )

    private fun names(items: List<TimelineItem>) = items.map {
        when (it) {
            is TimelineItem.OfTask -> "t${it.entry.task.id}"
            is TimelineItem.OfHabit -> "h${it.entry.habit.id}"
            TimelineItem.Now -> "now"
        }
    }

    @Test
    fun `timed items are in time order with now among them, done ones in place`() {
        val agenda = TodayAgenda(
            date = today,
            today = listOf(task(1, "13:00"), task(2, "07:00", done = true), task(3, null)),
            habits = listOf(habit(4, "21:00"), habit(5, null)),
        )
        val timeline = agenda.timeline(LocalTime.parse("10:15"))
        assertEquals(listOf("t2", "now", "t1", "h4"), names(timeline.timed))
        assertEquals(listOf("t3", "h5"), names(timeline.anyTime))
        assertTrue(timeline.hasLine)
    }

    @Test
    fun `now goes last after everything, and an item at this minute is still ahead`() {
        val agenda = TodayAgenda(date = today, today = listOf(task(1, "07:00"), task(2, "09:00")))
        assertEquals(listOf("t1", "t2", "now"), names(agenda.timeline(LocalTime.parse("22:00")).timed))
        assertEquals(listOf("t1", "now", "t2"), names(agenda.timeline(LocalTime.parse("08:59")).timed))
        assertEquals(listOf("t1", "t2", "now"), names(agenda.timeline(LocalTime.parse("09:00")).timed))
    }

    @Test
    fun `at the same time tasks come first, higher priority first`() {
        val agenda = TodayAgenda(
            date = today,
            today = listOf(task(1, "08:00"), task(2, "08:00", priority = Priority.HIGH)),
            habits = listOf(habit(3, "08:00")),
        )
        assertEquals(listOf("now", "t2", "t1", "h3"), names(agenda.timeline(LocalTime.parse("07:00")).timed))
    }

    @Test
    fun `no times means no line and no now`() {
        val agenda = TodayAgenda(date = today, today = listOf(task(1, null)), undated = listOf(TaskEntry(Task(id = 2, title = "x"), null, false)))
        val timeline = agenda.timeline(LocalTime.NOON)
        assertEquals(emptyList<String>(), names(timeline.timed))
        assertFalse(timeline.hasLine)
        assertEquals(listOf(2L), timeline.undated.map { it.task.id })
    }

    @Test
    fun `late tasks alone still make a line`() {
        val agenda = TodayAgenda(date = today, overdue = listOf(task(1, null, overdue = true)))
        val timeline = agenda.timeline(LocalTime.NOON)
        assertTrue(timeline.hasLine)
        assertEquals(listOf(1L), timeline.late.map { it.task.id })
    }
}
