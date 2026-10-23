package app.still.launcher.productivity

import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.QuotaPeriod
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// 2026-10-23 is a Friday; 2026-10-19 is the Monday of that week.
class HabitStatsTest {

    private val today = LocalDate.parse("2026-10-23")

    private fun date(text: String) = LocalDate.parse(text)

    private fun record(on: String, value: Double = 1.0, skipped: Boolean = false) =
        OccurrenceRecord(itemType = ItemType.HABIT, itemId = 1, date = date(on), value = value, skipped = skipped)

    private fun daily(start: String = "2026-10-15", measure: HabitMeasure = HabitMeasure.CHECK, target: Double = 1.0) =
        Habit(id = 1, title = "Read", measure = measure, target = target, schedule = Schedule(date(start), Recurrence.Daily()))

    private fun stats(habit: Habit, vararg records: OccurrenceRecord) =
        habitStats(habit, records.toList(), today, ZoneOffset.UTC)

    @Test
    fun `streak counts back from today when today is done`() {
        val result = stats(daily(), record("2026-10-21"), record("2026-10-22"), record("2026-10-23"))
        assertEquals(3, result.currentStreak)
        assertEquals(StreakUnit.OCCURRENCES, result.unit)
    }

    @Test
    fun `today not done yet doesn't break the streak`() {
        val result = stats(daily(), record("2026-10-21"), record("2026-10-22"))
        assertEquals(2, result.currentStreak)
        assertEquals(DayStatus.PENDING, result.history.last().second)
    }

    @Test
    fun `best streak survives a later miss`() {
        // 15–19 done (5), 20 missed, 21–22 done, today pending.
        val records = listOf("15", "16", "17", "18", "19", "21", "22").map { record("2026-10-$it") }.toTypedArray()
        val result = stats(daily(), *records)
        assertEquals(2, result.currentStreak)
        assertEquals(5, result.bestStreak)
    }

    @Test
    fun `skipping and partial progress both break a streak`() {
        assertEquals(0, stats(daily(), record("2026-10-21"), record("2026-10-22", skipped = true)).currentStreak)

        val water = daily(measure = HabitMeasure.COUNT, target = 8.0)
        val result = stats(water, record("2026-10-21", value = 8.0), record("2026-10-22", value = 5.0))
        assertEquals(0, result.currentStreak)
        assertEquals(DayStatus.PARTIAL, result.history.first { it.first == date("2026-10-22") }.second)
    }

    @Test
    fun `days a habit isn't scheduled don't break the streak`() {
        val mondaysAndWednesdays = Habit(
            id = 1, title = "Gym",
            schedule = Schedule(date("2026-10-05"), Recurrence.Weekly(setOf(MONDAY, WEDNESDAY))),
        )
        val result = stats(mondaysAndWednesdays, record("2026-10-12"), record("2026-10-14"), record("2026-10-19"), record("2026-10-21"))
        // 5 and 7 Oct were missed; the four after that are an unbroken run.
        assertEquals(4, result.currentStreak)
        assertEquals(DayStatus.NOT_DUE, result.history.first { it.first == date("2026-10-20") }.second)
    }

    @Test
    fun `recent rate is the share of due days done in the last 30 days`() {
        // Due 15–22 (8 days; today pending is left out), 6 done.
        val records = listOf("15", "16", "17", "19", "21", "22").map { record("2026-10-$it") }.toTypedArray()
        assertEquals(6.0 / 8.0, stats(daily(), *records).recentRate!!, 1e-9)
    }

    @Test
    fun `quota habits count weeks in which the quota was met`() {
        val swim = Habit(
            id = 1, title = "Swim", quota = Quota(2, QuotaPeriod.WEEK),
            createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        )
        val result = stats(
            swim,
            // Week of 5 Oct: met. Week of 12 Oct: met. This week: 1 of 2 so far.
            record("2026-10-06"), record("2026-10-08"),
            record("2026-10-13"), record("2026-10-15"),
            record("2026-10-20"),
        )
        assertEquals(StreakUnit.WEEKS, result.unit)
        assertEquals(2, result.currentStreak)
        assertEquals(2, result.bestStreak)
        assertNull(result.recentRate)
    }

    @Test
    fun `history covers the last sixteen weeks ending today`() {
        val history = stats(daily()).history
        assertEquals(HISTORY_DAYS.toInt(), history.size)
        assertEquals(today, history.last().first)
        assertEquals(DayStatus.NOT_DUE, history.first().second)
    }
}
