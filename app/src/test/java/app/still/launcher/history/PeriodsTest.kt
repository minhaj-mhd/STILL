package app.still.launcher.history

import app.still.launcher.data.history.FocusModePeriod
import app.still.launcher.data.history.InterventionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class PeriodsTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val friday = LocalDate.of(2026, 11, 6)

    private fun day(date: LocalDate, vararg apps: Pair<String, Long>, unlocks: Int = 0, counted: Long = 0, limit: Long = 0) = HistoryDay(
        day = date,
        apps = apps.associate { (name, millis) -> name to AppDay(millis, 1) },
        unlocks = unlocks,
        hourMillis = List(HOURS) { if (it == 5) apps.sumOf { app -> app.second } else 0L },
        limit = DayLimit(counted, limit),
    )

    @Test
    fun `weeks begin on the chosen day and months on the first`() {
        assertEquals(LocalDate.of(2026, 11, 2)..LocalDate.of(2026, 11, 8), periodDays(PeriodKind.WEEK, 0, friday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 10, 25)..LocalDate.of(2026, 10, 31), periodDays(PeriodKind.WEEK, -1, friday, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 9, 1)..LocalDate.of(2026, 9, 30), periodDays(PeriodKind.MONTH, -2, friday, DayOfWeek.MONDAY))
    }

    @Test
    fun `a period sums the shown apps, and today so far stays out of the average`() {
        val days = listOf(
            day(LocalDate.of(2026, 11, 2), "insta" to 2 * hour, "systemui" to hour, unlocks = 30),
            day(LocalDate.of(2026, 11, 3), "insta" to hour, "yt" to hour, unlocks = 20),
            day(friday, "insta" to 10 * minute, unlocks = 3),
        )
        val week = periodUsage(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), days, emptyList(), friday, 4) { it != "systemui" }
        assertEquals(listOf(2 * hour, 2 * hour, null, null, 10 * minute, null, null), week.dayMillis)
        assertEquals(4 * hour + 10 * minute, week.totalMillis)
        assertEquals(2 * hour, week.perDayMillis)
        assertEquals(25, week.unlocksPerDay)
        assertNull(week.perDayBeforeMillis)
        assertEquals(listOf("insta", "yt"), week.apps.map { it.packageName })
        assertEquals(AppTotal("insta", 3 * hour + 10 * minute, 3, perDayMillis = 90 * minute, perDayBeforeMillis = null), week.apps.first())
    }

    @Test
    fun `only today counts when it's all there is`() {
        val week = periodUsage(friday.minusDays(4), friday.plusDays(2), listOf(day(friday, "insta" to hour)), emptyList(), friday, 0) { true }
        assertEquals(hour, week.perDayMillis)
    }

    @Test
    fun `the period before is compared day for day, apps included`() {
        val before = listOf(day(LocalDate.of(2026, 10, 27), "insta" to 3 * hour), day(LocalDate.of(2026, 10, 28), "insta" to hour))
        val now = listOf(day(LocalDate.of(2026, 11, 2), "insta" to hour))
        val week = periodUsage(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), now, before, friday, 0) { true }
        assertEquals(2 * hour, week.perDayBeforeMillis)
        assertEquals(2 * hour, week.apps.single().perDayBeforeMillis)
    }

    @Test
    fun `hours start at the day's start, and days over the limit are marked`() {
        val days = listOf(
            day(LocalDate.of(2026, 11, 2), "insta" to hour, counted = 2 * hour, limit = 2 * hour),
            day(LocalDate.of(2026, 11, 3), "insta" to hour, counted = hour, limit = 3 * hour),
        )
        val week = periodUsage(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), days, emptyList(), friday, 4) { true }
        // Hour 5 is the second hour of a day starting at 4.
        assertEquals(2 * hour, week.hourMillis[1])
        assertEquals(listOf(true, false, false, false, false, false, false), week.overLimit)
        assertEquals(1, week.overLimitDays)
        assertEquals(3 * hour, week.limitMillis)
    }

    @Test
    fun `a month lists the weeks that touch it, averaged over their saved days`() {
        val days = listOf(
            day(LocalDate.of(2026, 9, 29), "insta" to 3 * hour),
            day(LocalDate.of(2026, 10, 1), "insta" to hour),
            day(LocalDate.of(2026, 10, 14), "insta" to hour),
        )
        val weeks = weekAverages(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), days, friday, DayOfWeek.MONDAY) { true }
        assertEquals(listOf(WeekAverage(LocalDate.of(2026, 9, 28), 2 * hour), WeekAverage(LocalDate.of(2026, 10, 12), hour)), weeks)
    }

    @Test
    fun `levels split against the busiest`() {
        assertEquals(listOf(0, 1, 2, 4, 4), listOf(0L, 1L, 50L, 100L, 120L).map { level(it, 100) })
        assertEquals(0, level(5, 0))
    }

    @Test
    fun `stepping in is tallied by kind`() {
        val tally = steppedIn(
            interventions = listOf(
                "insta" to InterventionType.BLOCKED,
                "insta" to InterventionType.SHORT_VIDEO,
                "yt" to InterventionType.SHORT_VIDEO,
                "yt" to InterventionType.TIME_UP,
                "insta" to InterventionType.PAUSE,
            ),
            choices = listOf("insta" to true, "insta" to false, "yt" to true),
        )
        assertEquals(SteppedIn(turnedBack = 2, paused = 3, keptClosed = 1, shortVideos = 2, ranOut = 1, mostOften = "insta"), tally)
        assertFalse(tally.isEmpty)
        assertTrue(steppedIn(emptyList(), emptyList()).isEmpty)
    }

    @Test
    fun `focus mode time is cut at the period's edges`() {
        val from = Instant.parse("2026-11-02T04:00:00Z")
        val to = Instant.parse("2026-11-09T04:00:00Z")
        val periods = listOf(
            FocusModePeriod(Instant.parse("2026-11-02T03:00:00Z"), Instant.parse("2026-11-02T05:00:00Z")),
            FocusModePeriod(Instant.parse("2026-11-04T10:00:00Z"), Instant.parse("2026-11-04T10:30:00Z")),
            FocusModePeriod(Instant.parse("2026-11-01T10:00:00Z"), Instant.parse("2026-11-01T11:00:00Z")),
        )
        assertEquals(Duration.ofMinutes(90) to 2, focusModeTime(periods, from, to))
    }
}
