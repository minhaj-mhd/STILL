package app.still.launcher.productivity

import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.Priority
import app.still.launcher.data.productivity.Task
import app.still.launcher.schedule.MissedPolicy
import app.still.launcher.schedule.Occurrence
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.QuotaPeriod
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// 2026-10-23 is a Friday; its week runs Monday 19 to Sunday 25 October. Times are UTC.
class AgendaTest {

    private val today = LocalDate.parse("2026-10-23")
    private val zone = ZoneOffset.UTC

    private fun date(text: String) = LocalDate.parse(text)
    private fun at(text: String) = Instant.parse(text)

    private fun agenda(
        tasks: List<Task> = emptyList(),
        habits: List<Habit> = emptyList(),
        records: List<OccurrenceRecord> = emptyList(),
        on: LocalDate = today,
    ) = buildAgenda(on, tasks, habits, records, zone)

    private fun done(type: ItemType, id: Long, on: String, value: Double = 1.0) =
        OccurrenceRecord(itemType = type, itemId = id, date = date(on), value = value)

    @Test
    fun `undated tasks show until done, and on the day they were done`() {
        val open = Task(id = 1, title = "Call bank")
        val doneToday = Task(id = 2, title = "Pay rent", completedAt = at("2026-10-23T09:00:00Z"))
        val doneYesterday = Task(id = 3, title = "Buy milk", completedAt = at("2026-10-22T09:00:00Z"))

        val undated = agenda(tasks = listOf(open, doneToday, doneYesterday)).undated
        assertEquals(listOf(1L, 2L), undated.map { it.task.id })
        assertTrue(undated[1].isDone)
    }

    @Test
    fun `one-time tasks are due on their day and overdue afterwards`() {
        val dueToday = Task(id = 1, title = "Submit report", schedule = Schedule(today))
        val dueEarlier = Task(id = 2, title = "Renew licence", schedule = Schedule(date("2026-10-20")))
        val dueLater = Task(id = 3, title = "Dentist", schedule = Schedule(date("2026-10-30")))
        val missedNotCarried = Task(
            id = 4, title = "Webinar", schedule = Schedule(date("2026-10-21")), missedPolicy = MissedPolicy.MISS,
        )

        val result = agenda(tasks = listOf(dueToday, dueEarlier, dueLater, missedNotCarried))
        assertEquals(listOf(1L), result.today.map { it.task.id })
        assertEquals(listOf(2L), result.overdue.map { it.task.id })
        assertEquals(2, result.pendingCount)
    }

    @Test
    fun `a one-time task ticked off today stays visible as done`() {
        val task = Task(id = 1, title = "Submit report", schedule = Schedule(date("2026-10-20")), completedAt = at("2026-10-23T08:00:00Z"))
        val result = agenda(tasks = listOf(task))
        assertTrue(result.overdue.isEmpty())
        assertTrue(result.today.single().isDone)
        assertEquals(0, result.pendingCount)
    }

    @Test
    fun `recurring task shows today, plus its last missed day`() {
        val daily = Task(id = 7, title = "Inbox zero", schedule = Schedule(date("2026-10-01"), Recurrence.Daily()))

        val missedYesterday = agenda(tasks = listOf(daily))
        assertEquals(listOf(date("2026-10-22")), missedYesterday.overdue.map { it.occurrence?.date })
        assertEquals(listOf(today), missedYesterday.today.map { it.occurrence?.date })

        val doneYesterdayAndToday = agenda(
            tasks = listOf(daily),
            records = listOf(done(ItemType.TASK, 7, "2026-10-22"), done(ItemType.TASK, 7, "2026-10-23")),
        )
        assertTrue(doneYesterdayAndToday.overdue.isEmpty())
        assertTrue(doneYesterdayAndToday.today.single().isDone)
    }

    @Test
    fun `skipping today removes it from pending but keeps it visible`() {
        val daily = Task(id = 7, title = "Inbox zero", schedule = Schedule(today, Recurrence.Daily()))
        val skipped = OccurrenceRecord(itemType = ItemType.TASK, itemId = 7, date = today, skipped = true)
        val entry = agenda(tasks = listOf(daily), records = listOf(skipped)).today.single()
        assertTrue(entry.isSkipped)
        assertFalse(entry.isPending)
    }

    @Test
    fun `subtasks and archived tasks are left out`() {
        val parent = Task(id = 1, title = "Move house", schedule = Schedule(today))
        val child = Task(id = 2, title = "Pack books", parentId = 1, schedule = Schedule(today))
        val archived = Task(id = 3, title = "Old", schedule = Schedule(today), archived = true)
        assertEquals(listOf(1L), agenda(tasks = listOf(parent, child, archived)).today.map { it.task.id })
    }

    @Test
    fun `parent rows carry their subtasks, oldest first`() {
        val parent = Task(id = 1, title = "Move house", schedule = Schedule(today))
        val open = Task(id = 3, title = "Book van", parentId = 1, createdAt = at("2026-10-21T10:00:00Z"))
        val packed = Task(id = 2, title = "Pack books", parentId = 1, createdAt = at("2026-10-20T10:00:00Z"))
        val other = Task(id = 4, title = "Unrelated", parentId = 9)
        val entry = agenda(tasks = listOf(parent, open, packed, other)).today.single()
        assertEquals(listOf(2L, 3L), entry.subtasks.map { it.id })
    }

    @Test
    fun `habits track progress against their target`() {
        val water = Habit(
            id = 1, title = "Water", measure = HabitMeasure.COUNT, target = 8.0,
            schedule = Schedule(date("2026-10-01"), Recurrence.Daily()),
        )
        val halfway = agenda(habits = listOf(water), records = listOf(done(ItemType.HABIT, 1, "2026-10-23", value = 4.0)))
            .habits.single()
        assertEquals(4.0, halfway.progress, 0.0)
        assertTrue(halfway.isPending)
        assertFalse(halfway.isDone)

        val finished = agenda(habits = listOf(water), records = listOf(done(ItemType.HABIT, 1, "2026-10-23", value = 8.0)))
            .habits.single()
        assertTrue(finished.isDone)
        assertFalse(finished.isPending)
    }

    @Test
    fun `habits not scheduled today don't appear`() {
        val mondays = Habit(id = 1, title = "Weekly review", schedule = Schedule(date("2026-10-05"), Recurrence.Weekly(setOf(java.time.DayOfWeek.MONDAY))))
        assertTrue(agenda(habits = listOf(mondays)).habits.isEmpty())
    }

    @Test
    fun `quota habits always show and are pending only when behind pace`() {
        val swim = Habit(id = 3, title = "Swim", quota = Quota(3, QuotaPeriod.WEEK))
        val mondayAndTuesday = listOf(done(ItemType.HABIT, 3, "2026-10-19"), done(ItemType.HABIT, 3, "2026-10-20"))

        // Friday with 2 of 3 done: 1 needed, 3 days left.
        val friday = agenda(habits = listOf(swim), records = mondayAndTuesday).habits.single()
        assertEquals(2, friday.quotaDone)
        assertFalse(friday.isPending)

        // Friday with only 0 of 3 done: 3 needed, 3 days left.
        assertTrue(agenda(habits = listOf(swim)).habits.single().isPending)

        // Last week's sessions don't count this week.
        val lastWeek = listOf(done(ItemType.HABIT, 3, "2026-10-16"))
        assertEquals(0, agenda(habits = listOf(swim), records = lastWeek).habits.single().quotaDone)
    }

    @Test
    fun `unfinished higher-priority tasks come first`() {
        val low = Task(id = 1, title = "Low", priority = Priority.LOW, schedule = Schedule(today), createdAt = at("2026-10-01T00:00:00Z"))
        val high = Task(id = 2, title = "High", priority = Priority.HIGH, schedule = Schedule(today), createdAt = at("2026-10-02T00:00:00Z"))
        val doneHigh = Task(
            id = 3, title = "Done", priority = Priority.HIGH, schedule = Schedule(today),
            completedAt = at("2026-10-23T07:00:00Z"),
        )
        assertEquals(listOf(2L, 1L, 3L), agenda(tasks = listOf(low, doneHigh, high)).today.map { it.task.id })
    }

    @Test
    fun `time slots give one habit row each`() {
        val meds = Habit(
            id = 5, title = "Meds",
            schedule = Schedule(today, Recurrence.Daily(), times = listOf(java.time.LocalTime.of(8, 0), java.time.LocalTime.of(20, 0))),
        )
        val morningDone = OccurrenceRecord(
            itemType = ItemType.HABIT, itemId = 5, date = today,
            slotMinute = OccurrenceRecord.slotMinuteOf(Occurrence(today, java.time.LocalTime.of(8, 0))), value = 1.0,
        )
        val rows = agenda(habits = listOf(meds), records = listOf(morningDone)).habits
        assertEquals(2, rows.size)
        // The pending evening dose sorts before the finished morning one.
        assertEquals(java.time.LocalTime.of(20, 0), rows[0].occurrence.time)
        assertTrue(rows[1].isDone)
    }
}
