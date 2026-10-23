package app.still.launcher.blocking

import app.still.launcher.data.blocking.BlockingSchedule
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

// 2026-10-23 is a Friday; 2026-10-19 is the Monday of that week.
class ScheduledBlocksTest {

    private fun at(text: String) = LocalDateTime.parse(text)

    private fun schedule(
        startMinute: Int,
        endMinute: Int,
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        interval: Int = 1,
        packages: Set<String> = setOf("insta"),
        name: String = "Evening",
        enabled: Boolean = true,
    ) = BlockingSchedule(
        name = name,
        schedule = Schedule(LocalDate.parse("2026-10-19"), Recurrence.Weekly(days, interval)),
        startMinute = startMinute,
        endMinute = endMinute,
        packages = packages,
        enabled = enabled,
    )

    @Test
    fun `a daytime window blocks only between its times`() {
        val morning = schedule(6 * 60, 9 * 60)
        assertNull(morning.activeWindow(at("2026-10-23T05:59")))
        assertEquals(at("2026-10-23T09:00"), morning.activeWindow(at("2026-10-23T06:00"))?.endsAt)
        assertNull(morning.activeWindow(at("2026-10-23T09:00")))
    }

    @Test
    fun `an overnight window runs into the next morning`() {
        val evening = schedule(21 * 60, 7 * 60)
        assertEquals(at("2026-10-24T07:00"), evening.activeWindow(at("2026-10-23T22:30"))?.endsAt)
        val early = evening.activeWindow(at("2026-10-24T06:59"))
        assertEquals(LocalDate.parse("2026-10-23"), early?.startDate)
        assertNull(evening.activeWindow(at("2026-10-24T07:00")))
    }

    @Test
    fun `days belong to the night they start`() {
        // Weekday evenings only: Friday night runs into Saturday morning, Saturday night doesn't exist.
        val weekdays = schedule(21 * 60, 7 * 60, days = DayOfWeek.entries.take(5).toSet())
        assertTrue(weekdays.activeWindow(at("2026-10-24T03:00")) != null)
        assertNull(weekdays.activeWindow(at("2026-10-24T22:00")))
        assertNull(weekdays.activeWindow(at("2026-10-25T03:00")))
    }

    @Test
    fun `every other week skips alternate weeks`() {
        val fortnightly = schedule(6 * 60, 9 * 60, interval = 2)
        assertTrue(fortnightly.activeWindow(at("2026-10-19T07:00")) != null)
        assertNull(fortnightly.activeWindow(at("2026-10-26T07:00")))
        assertTrue(fortnightly.activeWindow(at("2026-11-02T07:00")) != null)
    }

    @Test
    fun `the longest running schedule wins and disabled ones are ignored`() {
        val short = schedule(20 * 60, 22 * 60, packages = setOf("insta", "tiktok"), name = "Short")
        val long = schedule(20 * 60, 7 * 60, packages = setOf("insta"), name = "Long")
        val off = schedule(0, 23 * 60 + 59, packages = setOf("maps"), enabled = false)
        val blocks = scheduledBlocks(listOf(short, long, off), at("2026-10-23T21:00"))
        assertEquals(ScheduledBlock(at("2026-10-24T07:00"), "Long"), blocks["insta"])
        assertEquals(ScheduledBlock(at("2026-10-23T22:00"), "Short"), blocks["tiktok"])
        assertFalse("maps" in blocks)
    }

    @Test
    fun `off for today stops the running window and comes back tomorrow`() {
        val evening = schedule(21 * 60, 7 * 60)
        // Turned off at 01:00, during the window that started last night.
        val paused = evening.offForToday(at("2026-10-24T01:00"))
        assertNull(paused.activeWindow(at("2026-10-24T01:00")))
        assertTrue(paused.isOffForToday(at("2026-10-24T01:00")))
        // Tonight's window starts as usual.
        assertTrue(paused.activeWindow(at("2026-10-24T21:30")) != null)

        val resumed = paused.backOn(at("2026-10-24T01:00"))
        assertTrue(resumed.activeWindow(at("2026-10-24T01:00")) != null)
    }

    @Test
    fun `off for today before the window starts skips tonight`() {
        val evening = schedule(21 * 60, 7 * 60)
        val paused = evening.offForToday(at("2026-10-23T18:00"))
        assertNull(paused.activeWindow(at("2026-10-23T22:00")))
        assertTrue(paused.activeWindow(at("2026-10-24T22:00")) != null)
    }
}
