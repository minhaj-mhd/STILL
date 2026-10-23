package app.still.launcher.blocking

import app.still.launcher.blocking.AppUsageEvent.Kind.BACKGROUND
import app.still.launcher.blocking.AppUsageEvent.Kind.FOREGROUND
import app.still.launcher.blocking.AppUsageEvent.Kind.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeTest {

    private val minute = 60_000L

    private fun event(app: String, atMinute: Long, kind: AppUsageEvent.Kind) = AppUsageEvent(app, atMinute * minute, kind)

    @Test
    fun `time between coming to the front and leaving it is counted`() {
        val events = listOf(
            event("insta", 10, FOREGROUND), event("insta", 25, BACKGROUND),
            event("maps", 25, FOREGROUND), event("maps", 30, BACKGROUND),
            event("insta", 40, FOREGROUND), event("insta", 45, BACKGROUND),
        )
        assertEquals(mapOf("insta" to 20 * minute, "maps" to 5 * minute), foregroundTimeByApp(events, 0, 60 * minute))
    }

    @Test
    fun `screen off ends every session`() {
        val events = listOf(event("insta", 10, FOREGROUND), event("android", 20, SCREEN_OFF), event("insta", 50, BACKGROUND))
        assertEquals(mapOf("insta" to 10 * minute), foregroundTimeByApp(events, 0, 60 * minute))
    }

    @Test
    fun `an app open at the window's start counts from the start`() {
        val events = listOf(event("insta", 15, BACKGROUND))
        assertEquals(mapOf("insta" to 5 * minute), foregroundTimeByApp(events, 10 * minute, 60 * minute))
    }

    @Test
    fun `an app still open at the window's end counts until the end`() {
        val events = listOf(event("insta", 50, FOREGROUND))
        assertEquals(mapOf("insta" to 10 * minute), foregroundTimeByApp(events, 0, 60 * minute))
    }

    @Test
    fun `switching between screens of one app doesn't double count`() {
        // Screen A resumes, screen B resumes before A pauses, then A pauses and later B pauses.
        val events = listOf(
            event("insta", 0, FOREGROUND), event("insta", 5, FOREGROUND),
            event("insta", 5, BACKGROUND), event("insta", 6, FOREGROUND), event("insta", 20, BACKGROUND),
        )
        assertEquals(mapOf("insta" to 19 * minute), foregroundTimeByApp(events, 0, 60 * minute))
    }

    @Test
    fun `the app in front is the last one to come forward`() {
        val events = listOf(event("still", 0, FOREGROUND), event("still", 1, BACKGROUND), event("insta", 1, FOREGROUND))
        assertEquals("insta", foregroundAppAfter(events))
    }

    @Test
    fun `switching apps clears nothing when the next app came first`() {
        // Android may report the new app's resume before the old app's pause.
        val events = listOf(event("insta", 0, FOREGROUND), event("maps", 5, FOREGROUND), event("insta", 5, BACKGROUND))
        assertEquals("maps", foregroundAppAfter(events))
    }

    @Test
    fun `nothing is in front after leaving or turning the screen off`() {
        assertEquals(null, foregroundAppAfter(listOf(event("insta", 0, FOREGROUND), event("insta", 3, BACKGROUND))))
        assertEquals(null, foregroundAppAfter(listOf(event("insta", 0, FOREGROUND), event("android", 3, SCREEN_OFF))))
        assertEquals(null, foregroundAppAfter(emptyList()))
    }

    @Test
    fun `a stray leave after a session isn't counted again`() {
        val events = listOf(event("insta", 0, FOREGROUND), event("insta", 10, BACKGROUND), event("insta", 30, BACKGROUND))
        assertEquals(mapOf("insta" to 10 * minute), foregroundTimeByApp(events, 0, 60 * minute))
    }
}
