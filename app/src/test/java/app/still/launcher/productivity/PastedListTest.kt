package app.still.launcher.productivity

import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.Priority
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.QuotaPeriod
import app.still.launcher.schedule.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// 2026-10-26 is a Monday.
class PastedListTest {

    private val today = LocalDate.parse("2026-10-26")
    private val created = Instant.parse("2026-10-26T08:00:00Z")

    private fun task(item: PastedItem, start: LocalDate = today, datelessOnStart: Boolean = true) =
        item.resolve(start, today, datelessOnStart, created) as ResolvedItem.NewTask

    private fun habit(item: PastedItem, start: LocalDate = today) =
        (item.resolve(start, today, datelessOnStart = true, createdAt = created) as ResolvedItem.NewHabit).habit

    @Test
    fun `the prompt's example reads without problems`() {
        val prompt = pastePrompt(today)
        assertTrue(prompt.contains("Today is Monday, 26 October 2026."))
        val list = parsePastedList(prompt.substringAfter("Example:").substringBefore("Today is"))

        assertEquals(emptyList<PasteProblem>(), list.problems)
        assertEquals("Exam week", list.name)
        assertEquals(listOf("Physics: chapter 3", "Chemistry: organic reactions", "Maths past paper"), list.items.map { it.title })
        assertEquals(listOf(1, 2, 3), list.items.map { it.planDay })

        val physics = list.items[0]
        assertEquals(PastedDay.ListDay(1), physics.day)
        assertEquals(listOf(LocalTime.of(7, 0)), physics.times)
        assertEquals(Priority.MEDIUM, physics.priority)
        assertEquals(listOf("Read the notes", "Solve 10 problems"), physics.subtasks)
        assertEquals(listOf("Sections 3.1 to 3.4"), physics.notes)
        assertEquals(Priority.HIGH, list.items[2].priority)
        assertTrue(list.items.none { it.isHabit || it.repeat != null })
    }

    @Test
    fun `a reply around a code block keeps only the block`() {
        val reply = """
            Sure! Here's a plan for you:

            ```still
            Week plan
            - Revise algebra @tomorrow
            ```

            Good luck with the exam!
        """.trimIndent()
        val list = parsePastedList(reply)
        assertEquals("Week plan", list.name)
        assertEquals(listOf("Revise algebra"), list.items.map { it.title })
        assertEquals(PastedDay.Tomorrow, list.items[0].day)
    }

    @Test
    fun `a plain list with no bullets is one task per line, indented lines are subtasks`() {
        val list = parsePastedList("Buy milk\nCall mum\n    ask about Sunday\nPay rent")
        assertNull(list.name)
        assertEquals(listOf("Buy milk", "Call mum", "Pay rent"), list.items.map { it.title })
        assertEquals(listOf("ask about Sunday"), list.items[1].subtasks)
        assertTrue(list.items.all { it.day == null })
    }

    @Test
    fun `markdown checkboxes, numbers, bold and deeper levels are all read`() {
        val list = parsePastedList(
            """
            ## **Morning routine**
            1. **Stretch** @7:00
            2. [ ] Plan the day
               - [x] Check calendar
                 - Look at next week
            * Breakfast — @8am
            """.trimIndent(),
        )
        assertEquals("Morning routine", list.name)
        assertEquals(listOf("Stretch", "Plan the day", "Breakfast"), list.items.map { it.title })
        assertEquals(listOf("Check calendar", "Look at next week"), list.items[1].subtasks)
        assertEquals(listOf(LocalTime.of(8, 0)), list.items[2].times)
    }

    @Test
    fun `day headings put the items under them on that day`() {
        val list = parsePastedList(
            """
            Exam week
            Day 1: Physics
            - Read chapter 3
            - Practice @day4
            Day 2
            - Chemistry
            Wednesday
            - Maths
            """.trimIndent(),
        )
        assertEquals("Exam week", list.name)
        assertEquals(
            listOf(PastedDay.ListDay(1), PastedDay.ListDay(4), PastedDay.ListDay(2), PastedDay.Weekday(DayOfWeek.WEDNESDAY)),
            list.items.map { it.day },
        )
    }

    @Test
    fun `short weekday names don't make a heading a day`() {
        val list = parsePastedList("SAT prep\n- Vocabulary")
        assertEquals("SAT prep", list.name)
        assertNull(list.items[0].day)
    }

    @Test
    fun `every kind of day tag`() {
        val days = parsePastedList(
            """
            - a @today
            - b @tomorrow
            - c @day 3
            - d @day12
            - e @fri
            - f @2026-11-02
            - g @nodate
            """.trimIndent(),
        ).items
        assertEquals(
            listOf(
                PastedDay.Today, PastedDay.Tomorrow, PastedDay.ListDay(3), PastedDay.ListDay(12),
                PastedDay.Weekday(DayOfWeek.FRIDAY), PastedDay.OnDate(LocalDate.parse("2026-11-02")), null,
            ),
            days.map { it.day },
        )
        assertTrue(days.last().noDate)
    }

    @Test
    fun `times in 24-hour and am-pm, and more than one`() {
        val item = parsePastedList("- Pills @8:00 @20:30 @7pm @12am @9.15").items.single()
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(20, 30), LocalTime.of(19, 0), LocalTime.of(0, 0), LocalTime.of(9, 15)),
            item.times,
        )
    }

    @Test
    fun `repeats`() {
        fun repeat(tag: String) = parsePastedList("- x $tag").items.single().repeat
        assertEquals(PastedRepeat.Days(1), repeat("@every day"))
        assertEquals(PastedRepeat.Days(2), repeat("@every 2 days"))
        assertEquals(PastedRepeat.Days(2), repeat("@every other day"))
        assertEquals(PastedRepeat.Days(1), repeat("@daily"))
        val mwf = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertEquals(PastedRepeat.Weeks(mwf, 1), repeat("@every mon wed fri"))
        assertEquals(PastedRepeat.Weeks(mwf, 1), repeat("@every mon,wed,fri"))
        assertEquals(PastedRepeat.Weeks(mwf, 1), repeat("@every Monday, Wednesday and Friday"))
        assertEquals(PastedRepeat.Weeks(setOf(DayOfWeek.TUESDAY), 2), repeat("@every 2 weeks on tue"))
        assertEquals(PastedRepeat.Weeks(emptySet(), 1), repeat("@every week"))
        assertEquals(PastedRepeat.Weeks(DayOfWeek.entries.take(5).toSet(), 1), repeat("@every weekday"))
        assertEquals(PastedRepeat.Weeks(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 1), repeat("@weekends"))
        assertEquals(PastedRepeat.Months(1), repeat("@every month"))
        assertEquals(PastedRepeat.Years, repeat("@every year"))
    }

    @Test
    fun `a repeat it can't read is reported and left out`() {
        val list = parsePastedList("- Gym @every blue moon")
        assertNull(list.items.single().repeat)
        assertEquals("Gym blue moon", list.items.single().title)
        assertEquals(PasteProblem.Kind.UNKNOWN_REPEAT, list.problems.single().kind)
    }

    @Test
    fun `until takes a list day, a date or the day tag after it`() {
        fun until(tags: String) = parsePastedList("- x @every day $tags").items.single().until
        assertEquals(PastedDay.ListDay(14), until("@until day14"))
        assertEquals(PastedDay.ListDay(14), until("@until day 14"))
        assertEquals(PastedDay.ListDay(7), until("@until @day7"))
        assertEquals(PastedDay.OnDate(LocalDate.parse("2026-12-15")), until("@until 2026-12-15"))
        val item = parsePastedList("- x @every day @until @day7").items.single()
        assertNull(item.day)
    }

    @Test
    fun `habit targets and quotas`() {
        val items = parsePastedList(
            """
            - Read @habit @20min
            - Water @habit @8x
            - Run @habit @5km @3/week
            - Meditate @habit @1h @every weekday
            """.trimIndent(),
        ).items
        assertEquals(PastedTarget(HabitMeasure.DURATION, 20.0), items[0].target)
        assertEquals(PastedTarget(HabitMeasure.COUNT, 8.0), items[1].target)
        assertEquals(PastedTarget(HabitMeasure.MEASUREMENT, 5.0, "km"), items[2].target)
        assertEquals(Quota(3, QuotaPeriod.WEEK), items[2].quota)
        assertEquals(PastedTarget(HabitMeasure.DURATION, 60.0), items[3].target)
        assertEquals(listOf("Read", "Water", "Run", "Meditate"), items.map { it.title })
    }

    @Test
    fun `a duration on a task or subtask stays in its title, and a quota on a task is reported`() {
        val list = parsePastedList("- Essay draft @45min @2/week\n  - Outline @10min")
        val item = list.items.single()
        assertEquals("Essay draft (45min)", item.title)
        assertEquals(listOf("Outline (10min)"), item.subtasks)
        assertNull(item.quota)
        assertEquals(PasteProblem(1, PasteProblem.Kind.HABIT_ONLY, "@2/week"), list.problems.single())
    }

    @Test
    fun `unknown tags stay in the title and are reported`() {
        val list = parsePastedList("- Email @sam about notes @tommorow !!")
        val item = list.items.single()
        assertEquals("Email @sam about notes @tommorow", item.title)
        assertEquals(Priority.MEDIUM, item.priority)
        assertEquals(listOf("@sam", "@tommorow"), list.problems.map { it.text })
    }

    @Test
    fun `priority marks and words`() {
        val items = parsePastedList("- a !\n- b !!\n- c !!!\n- d !high\n- e Done!").items
        assertEquals(listOf(Priority.LOW, Priority.MEDIUM, Priority.HIGH, Priority.HIGH, Priority.NONE), items.map { it.priority })
        assertEquals("e Done!", items[4].title)
    }

    @Test
    fun `steps under a habit go in its notes`() {
        val item = parsePastedList("- Stretch @habit @every day\n  - Neck\n  - Back").items.single()
        assertEquals(emptyList<String>(), item.subtasks)
        assertEquals(listOf("– Neck", "– Back"), item.notes)
    }

    @Test
    fun `too many items are cut off and reported`() {
        val list = parsePastedList((1..MAX_PASTED_ITEMS + 5).joinToString("\n") { "- Task $it" })
        assertEquals(MAX_PASTED_ITEMS, list.items.size)
        assertEquals(PasteProblem.Kind.TOO_MANY, list.problems.single().kind)
    }

    @Test
    fun `resolving puts list days, weekdays and plain tasks on dates`() {
        val start = LocalDate.parse("2026-10-27") // Tuesday
        val items = parsePastedList("- a @day1\n- b @day3\n- c @mon\n- d @today\n- e").items
        assertEquals(
            listOf("2026-10-27", "2026-10-29", "2026-11-02", "2026-10-26", "2026-10-27").map(LocalDate::parse),
            items.map { task(it, start).task.schedule?.start },
        )
        assertNull(task(items[4], start, datelessOnStart = false).task.schedule)
    }

    @Test
    fun `resolving a repeating task with times and an end`() {
        val item = parsePastedList("- Revise @every mon wed fri @18:00 @until day14").items.single()
        val schedule = task(item).task.schedule!!
        assertEquals(today, schedule.start)
        assertEquals(Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), schedule.recurrence)
        assertEquals(LocalDate.parse("2026-11-08"), schedule.until)
        assertEquals(listOf(LocalTime.of(18, 0)), schedule.times)
    }

    @Test
    fun `a repeating task asked for no date still starts on the start day`() {
        val item = parsePastedList("- Water plants @every week @nodate").items.single()
        assertEquals(Recurrence.Weekly(setOf(DayOfWeek.MONDAY)), task(item).task.schedule?.recurrence)
    }

    @Test
    fun `subtasks keep their order`() {
        val resolved = task(parsePastedList("- a\n  - one\n  - two\n  - three").items.single())
        assertEquals(listOf("one", "two", "three"), resolved.subtasks.sortedBy { it.createdAt }.map { it.title })
        assertTrue(resolved.subtasks.all { it.createdAt > resolved.task.createdAt })
    }

    @Test
    fun `resolving habits`() {
        val items = parsePastedList("- Read @habit @20min\n- Run @habit @3/week @7:00\n- Gym @habit @every tue thu @3/week").items
        val read = habit(items[0])
        assertEquals(Recurrence.Daily(), read.schedule?.recurrence)
        assertEquals(HabitMeasure.DURATION, read.measure)
        assertEquals(20.0, read.target, 0.0)

        val run = habit(items[1])
        assertNull(run.schedule)
        assertEquals(Quota(3, QuotaPeriod.WEEK), run.quota)

        val gym = habit(items[2])
        assertEquals(Recurrence.Weekly(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)), gym.schedule?.recurrence)
        assertEquals(Quota(3, QuotaPeriod.WEEK), gym.quota)
    }

    @Test
    fun `times on a habit counted per week are reported`() {
        val list = parsePastedList("- Run @habit @3/week @7:00")
        assertEquals(emptyList<LocalTime>(), list.items.single().times)
        assertEquals(PasteProblem.Kind.TIMES_WITHOUT_DAYS, list.problems.single().kind)
    }
}
