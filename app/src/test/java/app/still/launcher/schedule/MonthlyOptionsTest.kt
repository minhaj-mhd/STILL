package app.still.launcher.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate

class MonthlyOptionsTest {

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `mid-month date offers its day and its ordinal weekday`() {
        assertEquals(
            listOf(Recurrence.MonthlyOnDay(23), Recurrence.MonthlyOnWeekday(4, FRIDAY)),
            monthlyOptionsFor(date("2026-10-23")),
        )
    }

    @Test
    fun `a fifth weekday is only offered as the last one`() {
        assertEquals(
            listOf(Recurrence.MonthlyOnDay(30), Recurrence.MonthlyOnWeekday(-1, FRIDAY)),
            monthlyOptionsFor(date("2026-10-30")),
        )
    }

    @Test
    fun `a fourth weekday in the final week offers both fourth and last`() {
        assertEquals(
            listOf(Recurrence.MonthlyOnDay(25), Recurrence.MonthlyOnWeekday(4, SUNDAY), Recurrence.MonthlyOnWeekday(-1, SUNDAY)),
            monthlyOptionsFor(date("2026-10-25")),
        )
    }

    @Test
    fun `moving the start date carries the pattern along`() {
        assertEquals(Recurrence.MonthlyOnDay(30, 2), Recurrence.MonthlyOnDay(23, 2).followingStart(date("2026-10-30")))
        assertEquals(
            Recurrence.MonthlyOnWeekday(-1, FRIDAY),
            Recurrence.MonthlyOnWeekday(-1, SUNDAY).followingStart(date("2026-10-30")),
        )
        assertEquals(
            Recurrence.Weekly(setOf(MONDAY)),
            Recurrence.Weekly(setOf(FRIDAY)).followingStart(date("2026-10-26")),
        )
    }
}
