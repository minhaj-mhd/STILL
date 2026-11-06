package app.still.launcher.history

import app.still.launcher.blocking.AppUsageEvent
import app.still.launcher.blocking.AppUsageEvent.Kind.BACKGROUND
import app.still.launcher.blocking.AppUsageEvent.Kind.FOREGROUND
import app.still.launcher.blocking.AppUsageEvent.Kind.SCREEN_OFF
import app.still.launcher.blocking.AppUsageEvent.Kind.UNLOCK
import app.still.launcher.blocking.opensByApp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DayUsageTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val utc = ZoneOffset.UTC

    private fun event(app: String, atMinute: Long, kind: AppUsageEvent.Kind) = AppUsageEvent(app, atMinute * minute, kind)

    @Test
    fun `opening counts coming to the front after another app or the screen, not moving within one app`() {
        val events = listOf(
            event("still", 0, FOREGROUND),
            event("still", 1, BACKGROUND),
            event("insta", 1, FOREGROUND),
            // Another of Instagram's screens.
            event("insta", 2, BACKGROUND),
            event("insta", 2, FOREGROUND),
            event("insta", 3, BACKGROUND),
            event("still", 3, FOREGROUND),
            event("still", 4, BACKGROUND),
            event("insta", 4, FOREGROUND),
            event("android", 5, SCREEN_OFF),
            // Unlocking straight back into it.
            event("insta", 9, FOREGROUND),
        )
        assertEquals(mapOf("still" to 2, "insta" to 3), opensByApp(events, 0, 60 * minute))
    }

    @Test
    fun `opens before the window are left out`() {
        val events = listOf(event("insta", 5, FOREGROUND), event("insta", 20, BACKGROUND), event("x", 20, FOREGROUND))
        assertEquals(mapOf("x" to 1), opensByApp(events, 10 * minute, 60 * minute))
    }

    @Test
    fun `a day keeps each app's time and opens, STILL apart, and unlocks`() {
        val events = listOf(
            event("android", 0, UNLOCK),
            event("still", 0, FOREGROUND),
            event("still", 10, BACKGROUND),
            event("insta", 10, FOREGROUND),
            event("insta", 40, BACKGROUND),
            event("android", 40, SCREEN_OFF),
            event("android", 100, UNLOCK),
            event("x", 100, FOREGROUND),
            event("x", 105, BACKGROUND),
        )
        val day = dayUsage(events, 0, 24 * hour, ownPackage = "still", zone = utc)
        assertEquals(mapOf("insta" to AppDay(30 * minute, 1), "x" to AppDay(5 * minute, 1)), day.apps)
        assertEquals(10 * minute, day.stillMillis)
        assertEquals(35 * minute, day.screenMillis)
        assertEquals(2, day.opens)
        assertEquals(2, day.unlocks)
    }

    @Test
    fun `time is split across the clock hours it covers`() {
        // 00:50 to 02:10, and STILL's own time isn't in the hours.
        val events = listOf(
            event("insta", 50, FOREGROUND),
            event("insta", 130, BACKGROUND),
            event("still", 130, FOREGROUND),
            event("still", 140, BACKGROUND),
        )
        val hours = dayUsage(events, 0, 24 * hour, ownPackage = "still", zone = utc).hourMillis
        assertEquals(HOURS, hours.size)
        assertEquals(listOf(10 * minute, 60 * minute, 10 * minute, 0L), hours.take(4))
        assertEquals(80 * minute, hours.sum())
    }

    @Test
    fun `a day is cut at its edges, with an app open across them counted on each side`() {
        // Days starting at 04:00: Instagram from 03:30 to 04:20.
        val events = listOf(event("insta", 210, FOREGROUND), event("insta", 260, BACKGROUND))
        val day = dayUsage(events, 4 * hour, 28 * hour, ownPackage = "still", zone = utc)
        assertEquals(AppDay(20 * minute, 0), day.apps["insta"])
    }

    @Test
    fun `the first save skips the day Android's events begin partway into`() {
        val today = LocalDate.of(2026, 11, 6)
        assertEquals(
            (1..5).map { LocalDate.of(2026, 11, it) },
            daysToSave(lastSaved = null, earliestEventDay = LocalDate.of(2026, 10, 31), today = today),
        )
    }

    @Test
    fun `after that each finished day is saved once`() {
        val today = LocalDate.of(2026, 11, 6)
        assertEquals(listOf(LocalDate.of(2026, 11, 5)), daysToSave(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 10, 27), today))
        assertEquals(emptyList<LocalDate>(), daysToSave(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 10, 27), today))
        // Days Android no longer has can't be saved.
        assertEquals(listOf(LocalDate.of(2026, 11, 5)), daysToSave(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 4), today))
        // Without events there's nothing to go on.
        assertEquals(emptyList<LocalDate>(), daysToSave(LocalDate.of(2026, 11, 1), null, today))
    }
}
