package app.still.launcher.focus

import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.schedule.Occurrence
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class FocusSessionTest {

    private val today = LocalDate.parse("2026-10-23")
    private val start = 1_000_000L

    private fun session(minutes: Int = 15, slotMinute: Int = OccurrenceRecord.ALL_DAY) = FocusSession(
        habitId = 7,
        title = "Read",
        date = today,
        slotMinute = slotMinute,
        startMillis = start,
        minutes = minutes,
        startProgress = 0.0,
    )

    @Test
    fun `a timer runs for what's left of the target, at least a minute`() {
        assertEquals(15, timerMinutes(target = 15.0, progress = 0.0))
        assertEquals(10, timerMinutes(target = 15.0, progress = 5.0))
        assertEquals(1, timerMinutes(target = 15.0, progress = 14.5))
        assertEquals(1, timerMinutes(target = 15.0, progress = 20.0))
    }

    @Test
    fun `whole minutes are counted, never more than the timer's length`() {
        val timer = session(minutes = 15)
        assertEquals(0, timer.minutesDone(start + 59_000))
        assertEquals(4, timer.minutesDone(start + 4 * 60_000 + 30_000))
        assertEquals(15, timer.minutesDone(start + 60 * 60_000))
        assertEquals(0, timer.minutesDone(start - 60_000))
    }

    @Test
    fun `it's due once its time is up`() {
        val timer = session(minutes = 15)
        assertFalse(timer.isDue(start + 15 * 60_000 - 1))
        assertTrue(timer.isDue(start + 15 * 60_000))
    }

    @Test
    fun `it belongs to one occurrence of one habit`() {
        val morning = session(slotMinute = 7 * 60 + 30)
        assertEquals(Occurrence(today, LocalTime.of(7, 30)), morning.occurrence)
        assertTrue(morning.isFor(7, Occurrence(today, LocalTime.of(7, 30))))
        assertFalse(morning.isFor(7, Occurrence(today)))
        assertFalse(morning.isFor(8, Occurrence(today, LocalTime.of(7, 30))))
        assertEquals(Occurrence(today), session().occurrence)
    }

    @Test
    fun `a session survives being saved and read back`() {
        val timer = session(slotMinute = 18 * 60)
        val json = Json.encodeToString(FocusSession.serializer(), timer)
        assertEquals(timer, Json.decodeFromString(FocusSession.serializer(), json))
    }
}
