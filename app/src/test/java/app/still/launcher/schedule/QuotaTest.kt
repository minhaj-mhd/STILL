package app.still.launcher.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// 2026-10-23 is a Friday; its week runs Monday 19 to Sunday 25 October.
class QuotaTest {

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `periods cover the whole week or month`() {
        assertEquals(
            DateRange(date("2026-10-19"), date("2026-10-25")),
            QuotaPeriod.WEEK.rangeContaining(date("2026-10-23")),
        )
        assertEquals(
            DateRange(date("2026-02-01"), date("2026-02-28")),
            QuotaPeriod.MONTH.rangeContaining(date("2026-02-14")),
        )
    }

    @Test
    fun `three a week is behind pace only when every remaining day is needed`() {
        val swim = Quota(count = 3, period = QuotaPeriod.WEEK)
        // Friday, 1 done: 2 needed, 3 days left (Fri, Sat, Sun).
        assertFalse(swim.isBehindPace(done = 1, today = date("2026-10-23")))
        // Saturday, 1 done: 2 needed, 2 days left.
        assertTrue(swim.isBehindPace(done = 1, today = date("2026-10-24")))
        // Sunday, 0 done: the goal is already out of reach, which still counts as behind.
        assertTrue(swim.isBehindPace(done = 0, today = date("2026-10-25")))
        // Quota met.
        assertFalse(swim.isBehindPace(done = 3, today = date("2026-10-25")))
    }

    @Test
    fun `remaining never goes negative`() {
        assertEquals(0, Quota(2, QuotaPeriod.WEEK).remaining(done = 5))
    }
}
