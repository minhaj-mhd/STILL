package app.still.launcher.productivity

import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.Task
import app.still.launcher.schedule.Occurrence
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.QuotaPeriod
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RemindersTest {

    private val today = LocalDate.parse("2026-10-23")
    private val noon = today.atTime(12, 0)

    private fun time(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private val meds = Habit(
        id = 1, title = "Meds",
        schedule = Schedule(today.minusDays(10), Recurrence.Daily(), times = listOf(time(8), time(20))),
    )

    @Test
    fun `next reminder is the first future time slot`() {
        assertEquals(today.atTime(20, 0), nextReminderAt(noon, emptyList(), listOf(meds), emptyList()))
        assertEquals(today.plusDays(1).atTime(8, 0), nextReminderAt(today.atTime(20, 0), emptyList(), listOf(meds), emptyList()))
    }

    @Test
    fun `done or skipped occurrences don't remind`() {
        val eveningDone = OccurrenceRecord(
            itemType = ItemType.HABIT, itemId = 1, date = today,
            slotMinute = OccurrenceRecord.slotMinuteOf(Occurrence(today, time(20))), value = 1.0,
        )
        assertEquals(today.plusDays(1).atTime(8, 0), nextReminderAt(noon, emptyList(), listOf(meds), listOf(eveningDone)))

        val skipped = eveningDone.copy(value = 0.0, skipped = true)
        assertEquals(today.plusDays(1).atTime(8, 0), nextReminderAt(noon, emptyList(), listOf(meds), listOf(skipped)))
    }

    @Test
    fun `due window excludes its start and includes its end`() {
        val due = remindersBetween(today.atTime(7, 59), today.atTime(8, 0), emptyList(), listOf(meds), emptyList())
        assertEquals(listOf("Meds"), due.map { it.title })
        assertTrue(remindersBetween(today.atTime(8, 0), today.atTime(8, 5), emptyList(), listOf(meds), emptyList()).isEmpty())
    }

    @Test
    fun `tasks remind only when they have a time and aren't done`() {
        val timed = Task(id = 1, title = "Call dentist", schedule = Schedule(today, times = listOf(time(15))))
        val untimed = Task(id = 2, title = "Groceries", schedule = Schedule(today))
        val done = Task(id = 3, title = "Pay rent", schedule = Schedule(today, times = listOf(time(16))), completedAt = Instant.now())
        val reminders = remindersBetween(noon, noon.plusDays(1), listOf(timed, untimed, done), emptyList(), emptyList())
        assertEquals(listOf(1L), reminders.map { it.itemId })
        assertTrue(reminders.single().canMarkDone)
    }

    @Test
    fun `only done-or-not habits offer Done from the notification`() {
        val water = meds.copy(id = 2, title = "Water", measure = HabitMeasure.COUNT, target = 8.0)
        val reminders = remindersBetween(noon, noon.plusDays(1), emptyList(), listOf(meds, water), emptyList())
        assertTrue(reminders.first { it.title == "Meds" }.canMarkDone)
        assertFalse(reminders.first { it.title == "Water" }.canMarkDone)
    }

    @Test
    fun `home shows the latest reminder that went off and isn't done`() {
        val active = activeReminders(noon, emptyList(), listOf(meds), emptyList(), dismissed = emptySet())
        // Today's 08:00 replaces yesterday's undone 20:00; tonight's 20:00 hasn't gone off yet.
        assertEquals(listOf(today.atTime(8, 0)), active.map { it.at })
    }

    @Test
    fun `done, skipped or dismissed reminders leave home`() {
        val morning = Occurrence(today, time(8))
        val done = OccurrenceRecord(
            itemType = ItemType.HABIT, itemId = 1, date = today,
            slotMinute = OccurrenceRecord.slotMinuteOf(morning), value = 1.0,
        )
        // Once today's is done, yesterday's missed evening dose doesn't come back.
        assertTrue(activeReminders(noon, emptyList(), listOf(meds), listOf(done), emptySet()).isEmpty())
        assertTrue(activeReminders(noon, emptyList(), listOf(meds), listOf(done.copy(value = 0.0, skipped = true)), emptySet()).isEmpty())

        val key = activeReminders(noon, emptyList(), listOf(meds), emptyList(), emptySet()).single().key
        assertTrue(activeReminders(noon, emptyList(), listOf(meds), emptyList(), setOf(key)).isEmpty())
    }

    @Test
    fun `a one-time task stays on home until done`() {
        val call = Task(id = 7, title = "Call dentist", schedule = Schedule(today.minusDays(2), times = listOf(time(15))))
        assertEquals(listOf(7L), activeReminders(noon, listOf(call), emptyList(), emptyList(), emptySet()).map { it.itemId })
        assertTrue(activeReminders(noon, listOf(call.copy(completedAt = Instant.now())), emptyList(), emptyList(), emptySet()).isEmpty())
    }

    @Test
    fun `quota habits and empty data have no reminders`() {
        val swim = Habit(id = 3, title = "Swim", quota = Quota(3, QuotaPeriod.WEEK))
        assertNull(nextReminderAt(noon, emptyList(), listOf(swim), emptyList()))
        assertNull(nextReminderAt(LocalDateTime.MIN.plusYears(1), emptyList(), emptyList(), emptyList()))
    }
}
