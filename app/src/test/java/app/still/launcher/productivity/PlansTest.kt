package app.still.launcher.productivity

import app.still.launcher.data.productivity.Plan
import app.still.launcher.data.productivity.PlanPause
import app.still.launcher.data.productivity.Task
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// Plans from Monday 26 October 2026. Times are UTC.
class PlansTest {

    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.parse("2026-10-26")
    private val plan = Plan(id = 1, name = "Exam week", start = monday)

    private fun day(n: Int) = monday.plusDays(n - 1L)
    private fun doneOn(date: LocalDate): Instant = date.atTime(18, 0).toInstant(zone)

    private fun planTask(id: Long, day: Int, done: LocalDate? = null, planId: Long = 1) = Task(
        id = id,
        title = "Day $day task $id",
        schedule = Schedule(day(day)),
        planId = planId,
        planDay = day,
        completedAt = done?.let(::doneOn),
    )

    private fun List<Task>.dates(today: LocalDate, plans: List<Plan> = listOf(plan)) =
        withPlanDates(plans, today, zone).associate { it.id to it.schedule?.start }

    @Test
    fun `days finished on time keep their dates`() {
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2), planTask(3, 3))
        assertEquals(mapOf(1L to day(1), 2L to day(2), 3L to day(3)), tasks.dates(today = day(2)))
    }

    @Test
    fun `a missed day stays due today and the rest move along`() {
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2), planTask(3, 3))
        // Day 2 wasn't done on Tuesday; on Thursday it's still the day to do.
        val dates = tasks.dates(today = day(4))
        assertEquals(day(4), dates[2])
        assertEquals(day(5), dates[3])
        val progress = planProgress(plan, tasks, day(4), zone)
        assertEquals(2, progress.behind)
        assertEquals(day(3), progress.plannedEnd)
        assertEquals(day(5), progress.end)
    }

    @Test
    fun `a day finished late moves the later days by as much`() {
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2, done = day(3)), planTask(3, 3))
        assertEquals(day(4), tasks.dates(today = day(3))[3])
        val progress = planProgress(plan, tasks, day(3), zone)
        assertTrue(progress.days[1].isLate)
        assertEquals(1, progress.daysOnTime)
    }

    @Test
    fun `a day finished early doesn't pull the next one earlier`() {
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2))
        assertEquals(day(2), tasks.dates(today = day(1))[2])
    }

    @Test
    fun `finishing days early catches up a plan that had slipped`() {
        // Day 1 was done a day late, so day 2 moved to Wednesday; doing it on Tuesday as well puts day 3 back.
        val tasks = listOf(planTask(1, 1, done = day(2)), planTask(2, 2, done = day(2)), planTask(3, 3))
        assertEquals(day(3), tasks.dates(today = day(2))[3])
        assertEquals(0, planProgress(plan, tasks, day(2), zone).behind)
    }

    @Test
    fun `a day isn't finished until all its tasks are`() {
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 1), planTask(3, 2))
        val dates = tasks.dates(today = day(2))
        assertEquals(day(2), dates[2])
        assertEquals(day(3), dates[3])
        // The task already done keeps its own date.
        assertEquals(day(1), dates[1])
    }

    @Test
    fun `gaps between days are kept when the plan moves`() {
        val tasks = listOf(planTask(1, 1), planTask(3, 3), planTask(5, 5))
        val dates = tasks.dates(today = day(2))
        assertEquals(listOf(day(2), day(4), day(6)), listOf(dates[1], dates[3], dates[5]))
    }

    @Test
    fun `plans move on their own`() {
        val tasks = listOf(planTask(1, 1), planTask(2, 2), planTask(3, 1, planId = 2, done = day(1)), planTask(4, 2, planId = 2))
        val dates = tasks.dates(today = day(2), plans = listOf(plan, Plan(id = 2, name = "", start = monday)))
        assertEquals(day(3), dates[2])
        assertEquals(day(2), dates[4])
    }

    @Test
    fun `other tasks are left as they are`() {
        val others = listOf(
            Task(id = 1, title = "Dentist", schedule = Schedule(day(1))),
            Task(id = 2, title = "Revise", schedule = Schedule(day(1), Recurrence.Daily()), planId = 1, planDay = 1),
            Task(id = 3, title = "Someday", planId = 1, planDay = 2),
        )
        assertSame(others, others.withPlanDates(listOf(plan), day(5), zone))
    }

    @Test
    fun `days on weekdays skip the weekend`() {
        val weekdays = plan.copy(weekdays = Plan.WEEKDAYS)
        val tasks = (1..6).map { planTask(it.toLong(), it) }
        val dates = tasks.dates(today = day(1), plans = listOf(weekdays))
        assertEquals((1..5).map { day(it) } + day(8), (1L..6L).map { dates[it] })
        assertEquals(day(8), planProgress(weekdays, tasks, day(1), zone).end)
    }

    @Test
    fun `days on picked weekdays start on the first of them`() {
        // Starting on a Monday, with days on Tuesdays and Thursdays.
        val picked = plan.copy(weekdays = Plan.weekdaysOf(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)))
        val tasks = (1..3).map { planTask(it.toLong(), it) }
        val dates = tasks.dates(today = day(1), plans = listOf(picked))
        assertEquals(listOf(day(2), day(4), day(9)), (1L..3L).map { dates[it] })
    }

    @Test
    fun `a missed weekday moves the rest to the next weekdays`() {
        val weekdays = plan.copy(weekdays = Plan.WEEKDAYS)
        val tasks = (1..6).map { n -> planTask(n.toLong(), n, done = day(n).takeIf { n <= 4 }) }
        // Friday's day 5 is still due on Monday, and day 6 goes to Tuesday.
        val dates = tasks.dates(today = day(8), plans = listOf(weekdays))
        assertEquals(day(8), dates[5])
        assertEquals(day(9), dates[6])
    }

    @Test
    fun `a paused plan's open tasks wait off Today`() {
        val paused = plan.copy(pauses = listOf(PlanPause(from = day(2))))
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2), planTask(3, 3))
        assertEquals(mapOf(1L to day(1)), tasks.dates(today = day(4), plans = listOf(paused)))
        val progress = planProgress(paused, tasks, day(4), zone)
        assertEquals(PlanState.PAUSED, progress.state)
        assertNull(progress.days[1].due)
        assertNull(progress.end)
    }

    @Test
    fun `after a pause the plan carries on from where it was`() {
        // Paused on Tuesday and resumed on Thursday: day 2 is Thursday, not two days behind.
        val resumed = plan.copy(pauses = listOf(PlanPause(from = day(2), until = day(4))))
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2), planTask(3, 3))
        val dates = tasks.dates(today = day(4), plans = listOf(resumed))
        assertEquals(day(4), dates[2])
        assertEquals(day(5), dates[3])
        assertEquals(0, planProgress(resumed, tasks, day(4), zone).behind)
    }

    @Test
    fun `a plan that keeps its dates leaves missed days overdue`() {
        val keeps = plan.copy(movesAlong = false)
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2), planTask(3, 3), planTask(4, 4))
        val dates = tasks.dates(today = day(4), plans = listOf(keeps))
        assertEquals(listOf(day(2), day(3), day(4)), listOf(dates[2], dates[3], dates[4]))
        assertEquals(2, planProgress(keeps, tasks, day(4), zone).behind)
    }

    @Test
    fun `a plan's state follows its days`() {
        val later = plan.copy(start = day(3))
        val tasks = listOf(planTask(1, 1), planTask(2, 2))
        assertEquals(PlanState.NOT_STARTED, planProgress(later, tasks, day(1), zone).state)
        assertEquals(PlanState.ACTIVE, planProgress(plan, tasks, day(1), zone).state)
        val done = listOf(planTask(1, 1, done = day(1)), planTask(2, 2, done = day(2)))
        val finished = planProgress(plan, done, day(5), zone)
        assertEquals(PlanState.FINISHED, finished.state)
        assertEquals(day(2), finished.end)
    }

    @Test
    fun `on the Today page a missed plan day is due today, not overdue, and the next day isn't shown`() {
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2), planTask(3, 3))
        val agenda = buildAgenda(day(4), tasks, emptyList(), emptyList(), zone, plans = listOf(plan))
        assertTrue(agenda.overdue.isEmpty())
        assertEquals(listOf(2L), agenda.today.map { it.task.id })
        assertEquals(PlanPlace("Exam week", 2, 3), agenda.today.single().plan)
        assertFalse(agenda.today.single().isOverdue)
    }

    @Test
    fun `a pasted plan puts tasks on its days and leaves habits out`() {
        val pasted = parsePastedList("Day 1\n- Physics @7:00\n  - Notes\nDay 3\n- Maths\n- Flashcards @habit\n- Gym @every day\n- Chemistry @day2")
        // From Friday 30 October on weekdays: days 1, 2 and 3 are Friday, Monday and Tuesday.
        val calendar = PlanCalendar(day(5), Plan.WEEKDAYS)
        val tasks = pasted.planTasks(calendar, today = day(5), createdAt = Instant.EPOCH)
        assertEquals(listOf("Physics", "Maths", "Gym", "Chemistry"), tasks.map { it.first.title })
        assertEquals(listOf(1, 3, 3, 2), tasks.map { it.first.planDay })
        assertEquals(listOf(day(5), day(9), day(9), day(8)), tasks.map { it.first.schedule?.start })
        assertTrue(tasks.none { it.first.schedule!!.isRecurring })
        assertEquals(listOf(LocalTime.of(7, 0)), tasks[0].first.schedule?.times)
        assertEquals(listOf("Notes"), tasks[0].second.map { it.title })
    }

    @Test
    fun `a plan day skipped today waits until tomorrow and the rest move along`() {
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2).copy(skippedOn = day(2)), planTask(3, 3))
        val dates = tasks.dates(today = day(2))
        assertEquals(day(3), dates[2])
        assertEquals(day(4), dates[3])
        // On the day it was skipped it shows as skipped, not pending.
        val entry = buildAgenda(day(2), tasks, emptyList(), emptyList(), zone, plans = listOf(plan)).today.single()
        assertEquals(2L, entry.task.id)
        assertTrue(entry.isSkipped)
        assertFalse(entry.isPending)
    }

    @Test
    fun `the day after, a skipped plan day is due again`() {
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2).copy(skippedOn = day(2)), planTask(3, 3))
        val moved = tasks.withPlanDates(listOf(plan), day(3), zone).associateBy { it.id }
        assertEquals(day(3), moved.getValue(2).schedule?.start)
        assertNull(moved.getValue(2).skippedOn)
        assertEquals(day(4), moved.getValue(3).schedule?.start)
        val entry = buildAgenda(day(3), tasks, emptyList(), emptyList(), zone, plans = listOf(plan)).today.single()
        assertEquals(2L, entry.task.id)
        assertTrue(entry.isPending)
    }

    @Test
    fun `a weekday plan skipped on Friday comes back on Monday`() {
        val weekdays = plan.copy(weekdays = Plan.WEEKDAYS)
        val tasks = (1..4).map { planTask(it.toLong(), it, done = day(it)) } +
            planTask(5, 5).copy(skippedOn = day(5)) + planTask(6, 6)
        val dates = tasks.dates(today = day(5), plans = listOf(weekdays))
        assertEquals(day(8), dates[5])
        assertEquals(day(9), dates[6])
    }

    @Test
    fun `a plan that keeps its dates lets a skipped day go`() {
        val keeps = plan.copy(movesAlong = false)
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2).copy(skippedOn = day(2)), planTask(3, 3))
        val progress = planProgress(keeps, tasks, day(4), zone)
        assertTrue(progress.days[1].excused)
        assertEquals(1, progress.behind)
        assertEquals(3, progress.current?.number)
        assertEquals(day(2), tasks.dates(today = day(4), plans = listOf(keeps))[2])
        // Shown as skipped on its day, then gone: never overdue.
        assertTrue(buildAgenda(day(2), tasks, emptyList(), emptyList(), zone, plans = listOf(keeps)).today.single().isSkipped)
        val later = buildAgenda(day(4), tasks, emptyList(), emptyList(), zone, plans = listOf(keeps))
        assertEquals(listOf(3L), later.overdue.map { it.task.id })
        assertTrue(later.today.isEmpty())
    }

    @Test
    fun `a day finished after a skip counts as done`() {
        val keeps = plan.copy(movesAlong = false)
        val tasks = listOf(planTask(1, 1, done = day(1)), planTask(2, 2, done = day(3)).copy(skippedOn = day(2)))
        val progress = planProgress(keeps, tasks, day(4), zone)
        assertTrue(progress.days[1].isFinished)
        assertFalse(progress.days[1].excused)
    }
}
