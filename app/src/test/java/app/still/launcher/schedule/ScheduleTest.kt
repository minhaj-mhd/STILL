package app.still.launcher.schedule

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime

// 2026-10-23 is a Friday; 2026-10-19 and 2026-10-26 are Mondays.
class ScheduleTest {

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `once occurs only on its date`() {
        val schedule = Schedule(start = date("2026-10-23"))
        assertTrue(schedule.occursOn(date("2026-10-23")))
        assertFalse(schedule.occursOn(date("2026-10-24")))
        assertFalse(schedule.occursOn(date("2026-10-22")))
    }

    @Test
    fun `daily interval counts from the start date`() {
        val schedule = Schedule(start = date("2026-10-01"), recurrence = Recurrence.Daily(interval = 2))
        assertTrue(schedule.occursOn(date("2026-10-01")))
        assertFalse(schedule.occursOn(date("2026-10-02")))
        assertTrue(schedule.occursOn(date("2026-10-03")))
        assertFalse(schedule.occursOn(date("2026-09-29")))
    }

    @Test
    fun `every other week alternates whole weeks`() {
        val schedule = Schedule(
            start = date("2026-10-05"),
            recurrence = Recurrence.Weekly(setOf(MONDAY, WEDNESDAY), interval = 2),
        )
        assertTrue(schedule.occursOn(date("2026-10-05")))
        assertTrue(schedule.occursOn(date("2026-10-07")))
        assertFalse(schedule.occursOn(date("2026-10-12")))
        assertFalse(schedule.occursOn(date("2026-10-14")))
        assertTrue(schedule.occursOn(date("2026-10-19")))
        assertFalse(schedule.occursOn(date("2026-10-20")))
    }

    @Test
    fun `weekly parity is anchored to the week of a mid-week start`() {
        // Starts Wednesday 7 Oct: that week's Monday is before the start, the next is an off week.
        val schedule = Schedule(start = date("2026-10-07"), recurrence = Recurrence.Weekly(setOf(MONDAY), interval = 2))
        assertFalse(schedule.occursOn(date("2026-10-05")))
        assertFalse(schedule.occursOn(date("2026-10-12")))
        assertTrue(schedule.occursOn(date("2026-10-19")))
    }

    @Test
    fun `monthly on the 31st falls back to the last day of short months`() {
        val schedule = Schedule(start = date("2026-01-31"), recurrence = Recurrence.MonthlyOnDay(31))
        assertTrue(schedule.occursOn(date("2026-02-28")))
        assertTrue(schedule.occursOn(date("2026-04-30")))
        assertFalse(schedule.occursOn(date("2026-04-29")))
        assertTrue(schedule.occursOn(date("2026-05-31")))
    }

    @Test
    fun `monthly on an ordinal weekday`() {
        val lastFriday = Schedule(start = date("2026-10-01"), recurrence = Recurrence.MonthlyOnWeekday(-1, FRIDAY))
        assertTrue(lastFriday.occursOn(date("2026-10-30")))
        assertFalse(lastFriday.occursOn(date("2026-10-23")))
        assertTrue(lastFriday.occursOn(date("2026-11-27")))

        val secondTuesday = Schedule(start = date("2026-10-01"), recurrence = Recurrence.MonthlyOnWeekday(2, TUESDAY))
        assertTrue(secondTuesday.occursOn(date("2026-10-13")))
        assertFalse(secondTuesday.occursOn(date("2026-10-06")))
    }

    @Test
    fun `monthly interval skips months`() {
        val schedule = Schedule(start = date("2026-01-15"), recurrence = Recurrence.MonthlyOnDay(15, interval = 3))
        assertTrue(schedule.occursOn(date("2026-04-15")))
        assertFalse(schedule.occursOn(date("2026-02-15")))
        assertTrue(schedule.occursOn(date("2027-01-15")))
    }

    @Test
    fun `yearly on 29 February uses the 28th in common years`() {
        val schedule = Schedule(start = date("2024-02-29"), recurrence = Recurrence.Yearly)
        assertTrue(schedule.occursOn(date("2025-02-28")))
        assertFalse(schedule.occursOn(date("2025-03-01")))
        assertTrue(schedule.occursOn(date("2028-02-29")))
        assertFalse(schedule.occursOn(date("2028-02-28")))
    }

    @Test
    fun `until, skipped dates and pauses remove occurrences`() {
        val schedule = Schedule(
            start = date("2026-10-01"),
            recurrence = Recurrence.Daily(),
            until = date("2026-10-15"),
            skippedDates = setOf(date("2026-10-05")),
            pauses = listOf(DateRange(date("2026-10-10"), date("2026-10-12"))),
        )
        assertFalse(schedule.occursOn(date("2026-10-05")))
        assertFalse(schedule.occursOn(date("2026-10-11")))
        assertTrue(schedule.occursOn(date("2026-10-13")))
        assertFalse(schedule.occursOn(date("2026-10-16")))
        assertEquals(11, schedule.occurrencesBetween(date("2026-09-01"), date("2026-12-31")).size)
    }

    @Test
    fun `time slots produce one occurrence each, in time order`() {
        val schedule = Schedule(
            start = date("2026-10-23"),
            recurrence = Recurrence.Daily(),
            times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0)),
        )
        assertEquals(
            listOf(
                Occurrence(date("2026-10-23"), LocalTime.of(8, 0)),
                Occurrence(date("2026-10-23"), LocalTime.of(20, 0)),
                Occurrence(date("2026-10-24"), LocalTime.of(8, 0)),
                Occurrence(date("2026-10-24"), LocalTime.of(20, 0)),
            ),
            schedule.occurrencesBetween(date("2026-10-23"), date("2026-10-24")),
        )
    }

    @Test
    fun `next and previous dates`() {
        val mondays = Schedule(start = date("2026-10-05"), recurrence = Recurrence.Weekly(setOf(MONDAY)))
        assertEquals(date("2026-10-26"), mondays.nextDate(date("2026-10-23")))
        assertEquals(date("2026-10-26"), mondays.nextDate(date("2026-10-26")))
        assertEquals(date("2026-10-19"), mondays.previousDate(date("2026-10-23")))
        assertNull(mondays.previousDate(date("2026-10-05")))

        val ended = mondays.copy(until = date("2026-10-20"))
        assertNull(ended.nextDate(date("2026-10-23")))
    }

    @Test
    fun `one-time item stays overdue until done when carried over`() {
        val schedule = Schedule(start = date("2026-10-20"))
        val occurrence = Occurrence(date("2026-10-20"))

        assertEquals(emptyList<Occurrence>(), schedule.pending(date("2026-10-19"), emptySet(), MissedPolicy.CARRY_OVER))
        assertEquals(listOf(occurrence), schedule.pending(date("2026-10-20"), emptySet(), MissedPolicy.CARRY_OVER))
        assertEquals(listOf(occurrence), schedule.pending(date("2026-10-23"), emptySet(), MissedPolicy.CARRY_OVER))
        assertEquals(emptyList<Occurrence>(), schedule.pending(date("2026-10-23"), setOf(occurrence), MissedPolicy.CARRY_OVER))
        assertEquals(emptyList<Occurrence>(), schedule.pending(date("2026-10-23"), emptySet(), MissedPolicy.MISS))
    }

    @Test
    fun `recurring item carries over only its latest missed day`() {
        val daily = Schedule(start = date("2026-10-01"), recurrence = Recurrence.Daily())
        val today = date("2026-10-23")

        assertEquals(
            listOf(Occurrence(date("2026-10-22")), Occurrence(today)),
            daily.pending(today, emptySet(), MissedPolicy.CARRY_OVER),
        )
        assertEquals(
            listOf(Occurrence(today)),
            daily.pending(today, setOf(Occurrence(date("2026-10-22"))), MissedPolicy.CARRY_OVER),
        )
        assertEquals(listOf(Occurrence(today)), daily.pending(today, emptySet(), MissedPolicy.MISS))
    }

    @Test
    fun `recurring item not due today can still be overdue`() {
        val mondays = Schedule(start = date("2026-10-05"), recurrence = Recurrence.Weekly(setOf(MONDAY)))
        assertEquals(
            listOf(Occurrence(date("2026-10-19"))),
            mondays.pending(date("2026-10-23"), emptySet(), MissedPolicy.CARRY_OVER),
        )
        assertEquals(emptyList<Occurrence>(), mondays.pending(date("2026-10-23"), emptySet(), MissedPolicy.MISS))
    }

    @Test
    fun `schedules survive a JSON round trip`() {
        val schedule = Schedule(
            start = date("2026-10-05"),
            recurrence = Recurrence.Weekly(setOf(MONDAY, FRIDAY), interval = 2),
            until = date("2027-10-05"),
            times = listOf(LocalTime.of(7, 30)),
            skippedDates = setOf(date("2026-12-25")),
            pauses = listOf(DateRange(date("2026-12-20"), date("2027-01-03"))),
        )
        val json = Json.encodeToString(Schedule.serializer(), schedule)
        assertEquals(schedule, Json.decodeFromString(Schedule.serializer(), json))
        assertEquals(Schedule(date("2026-10-23")), Json.decodeFromString(Schedule.serializer(), """{"start":"2026-10-23"}"""))
    }
}
