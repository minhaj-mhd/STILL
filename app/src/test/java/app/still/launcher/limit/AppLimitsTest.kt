package app.still.launcher.limit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class AppLimitsTest {

    private val monday = LocalDate.of(2026, 10, 19)
    private val tuesday = monday.plusDays(1)

    @Test
    fun `a first limit counts at once and marks its first day`() {
        val entry = null.withLimit(AppLimit(dailyMinutes = 30), monday)!!
        assertEquals(30, entry.limitOn(monday).dailyMinutes)
        assertEquals(monday, entry.startedOn)
    }

    @Test
    fun `on the first day limits can be loosened freely`() {
        val entry = null.withLimit(AppLimit(dailyMinutes = 30), monday).withLimit(AppLimit(dailyMinutes = 60), monday)!!
        assertEquals(60, entry.limitOn(monday).dailyMinutes)
        assertNull(entry.pending)
    }

    @Test
    fun `later a loosening waits until tomorrow`() {
        val entry = AppLimitEntry(AppLimit(dailyMinutes = 30), startedOn = monday).withLimit(AppLimit(dailyMinutes = 60), tuesday)!!
        assertEquals(30, entry.limitOn(tuesday).dailyMinutes)
        assertEquals(60, entry.limitOn(tuesday.plusDays(1)).dailyMinutes)
        assertEquals(tuesday.plusDays(1), entry.waitingOn(tuesday)?.from)
    }

    @Test
    fun `tightening counts at once and drops a waiting loosening`() {
        val waiting = AppLimitEntry(AppLimit(dailyMinutes = 30), PendingAppLimit(AppLimit(dailyMinutes = 60), tuesday.plusDays(1)), monday)
        val entry = waiting.withLimit(AppLimit(dailyMinutes = 20), tuesday)!!
        assertEquals(20, entry.limitOn(tuesday).dailyMinutes)
        assertNull(entry.pending)
        assertEquals(monday, entry.startedOn)
    }

    @Test
    fun `removing every limit is a loosening too`() {
        val entry = AppLimitEntry(AppLimit(dailyMinutes = 30, askWhy = true), startedOn = monday).withLimit(AppLimit(), tuesday)
        assertTrue(entry!!.limitOn(tuesday).isOn)
        assertFalse(entry.limitOn(tuesday.plusDays(1)).isOn)
    }

    @Test
    fun `removing limits on their first day forgets the app`() {
        assertNull(AppLimitEntry(AppLimit(dailyMinutes = 30), startedOn = monday).withLimit(AppLimit(), monday))
    }

    @Test
    fun `what counts as loosening`() {
        val limit = AppLimit(dailyMinutes = 30, visitMinutes = 10, waitMinutes = 30, askWhy = true)
        assertTrue(isLoosening(limit, limit.copy(dailyMinutes = 45)))
        assertTrue(isLoosening(limit, limit.copy(visitMinutes = 0)))
        assertTrue(isLoosening(limit, limit.copy(waitMinutes = 15)))
        assertTrue(isLoosening(limit, limit.copy(askWhy = false)))
        assertFalse(isLoosening(limit, limit.copy(dailyMinutes = 15, waitMinutes = 60)))
        assertFalse(isLoosening(AppLimit(), AppLimit(dailyMinutes = 30)))
    }

    @Test
    fun `an app is done for the day once its budget is used`() {
        val status = AppLimitStatus(AppLimit(dailyMinutes = 30), Duration.ofMinutes(30))
        assertEquals(AppClosed.DONE_TODAY, status.closed)
        assertEquals(Duration.ZERO, status.left)
        assertNull(AppLimitStatus(AppLimit(dailyMinutes = 30), Duration.ofMinutes(29)).closed)
    }

    @Test
    fun `after a visit the app waits, unless you come back within the grace`() {
        val limit = AppLimit(waitMinutes = 30)
        val visit = AppVisit(startMillis = 0, leftMillis = 10 * MINUTE)
        assertNull(opensAgainAt(limit, visit, nowMillis = 10 * MINUTE + 30_000, graceMillis = MINUTE))
        assertEquals(40 * MINUTE, opensAgainAt(limit, visit, nowMillis = 12 * MINUTE, graceMillis = MINUTE))
        assertNull(opensAgainAt(limit, visit, nowMillis = 41 * MINUTE, graceMillis = MINUTE))
    }

    @Test
    fun `a visit whose time ran out ends at once`() {
        val visit = AppVisit(startMillis = 0, leftMillis = 10 * MINUTE, timeUp = true)
        assertFalse(visit.isOngoing(10 * MINUTE + 1_000, MINUTE))
        assertEquals(40 * MINUTE, opensAgainAt(AppLimit(waitMinutes = 30), visit, 10 * MINUTE + 1_000, MINUTE))
    }

    @Test
    fun `a visit lasts its length or what's left of the day, whichever is shorter`() {
        assertEquals(10, visitLength(AppLimit(dailyMinutes = 30, visitMinutes = 10), Duration.ofMinutes(5)))
        assertEquals(4, visitLength(AppLimit(dailyMinutes = 30, visitMinutes = 10), Duration.ofSeconds(26 * 60 + 30)))
        assertEquals(30, visitLength(AppLimit(dailyMinutes = 30), Duration.ZERO))
        assertNull(visitLength(AppLimit(waitMinutes = 30), Duration.ZERO))
    }

    @Test
    fun `the pause asks now and then, always during focus, and when the app keeps coming back`() {
        val asks = AppLimit(askWhy = true)
        assertEquals(PauseContext.USUAL, pauseContext(asks, focusing = false, opensLastHour = 0, lastAskedMillis = null, nowMillis = 0))
        assertNull(pauseContext(asks, focusing = false, opensLastHour = 1, lastAskedMillis = 0, nowMillis = HOUR_MILLIS))
        assertEquals(PauseContext.USUAL, pauseContext(asks, false, 1, lastAskedMillis = 0, nowMillis = ASK_EVERY_MILLIS))
        assertEquals(PauseContext.FOCUS, pauseContext(asks, focusing = true, opensLastHour = 0, lastAskedMillis = 0, nowMillis = 1))
        assertEquals(PauseContext.OFTEN, pauseContext(asks, false, opensLastHour = OFTEN_OPENS, lastAskedMillis = 0, nowMillis = 1))
        assertNull(pauseContext(AppLimit(dailyMinutes = 30), focusing = true, opensLastHour = 9, lastAskedMillis = null, nowMillis = 0))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
