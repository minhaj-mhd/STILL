package app.still.launcher.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReadTest {

    private val minute = 60_000L

    @Test
    fun `reading counts while something happens, and stops counting a book left open`() {
        val clock = ReadingClock(idleMillis = 3 * minute)
        clock.activity(0)
        clock.activity(2 * minute)
        clock.activity(3 * minute)
        // Left open for ten minutes, then a page turn: only three of those count.
        clock.activity(13 * minute)
        assertEquals(9 * minute, clock.millisSoFar(16 * minute))
        assertEquals(ReadingClock.Sitting(0, 9 * minute), clock.stop(20 * minute))
        assertNull(clock.stop(21 * minute))
    }

    @Test
    fun `a sitting starts again after it's stopped`() {
        val clock = ReadingClock(idleMillis = 3 * minute)
        clock.activity(0)
        clock.stop(minute)
        clock.activity(10 * minute)
        assertEquals(ReadingClock.Sitting(10 * minute, 2 * minute), clock.stop(12 * minute))
    }

    @Test
    fun `the streak counts days meeting the goal, and today only once it does`() {
        val today = LocalDate.of(2026, 11, 6)
        val days = mapOf(
            today to 5 * minute,
            today.minusDays(1) to 20 * minute,
            today.minusDays(2) to 25 * minute,
            today.minusDays(3) to 5 * minute,
            today.minusDays(4) to 30 * minute,
        )
        assertEquals(2, readingStreak(days, 20 * minute, today))
        assertEquals(3, readingStreak(days + (today to 21 * minute), 20 * minute, today))
        // Without a goal any reading counts.
        assertEquals(5, readingStreak(days, 0, today))
        assertEquals(0, readingStreak(emptyMap(), 20 * minute, today))
    }

    @Test
    fun `progress through a PDF, and an EPUB's end`() {
        assertEquals(0.5, pdfProgress(1, 4), 0.0)
        assertEquals(1.0, pdfProgress(303, 304), 0.0)
        assertTrue(isAtEnd(0.998))
        assertFalse(isAtEnd(0.98))
        assertEquals(0.0, pdfProgress(0, 0), 0.0)
        assertEquals(12.0, readingMinutes(12 * minute + 59_000), 0.0)
    }
}
