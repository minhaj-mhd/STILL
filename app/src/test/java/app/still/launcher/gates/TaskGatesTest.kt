package app.still.launcher.gates

import app.still.launcher.data.gates.GateCondition
import app.still.launcher.data.gates.GateEvent
import app.still.launcher.data.gates.GateEventType
import app.still.launcher.data.gates.GateItem
import app.still.launcher.data.gates.GateWindow
import app.still.launcher.data.gates.TaskGate
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.Priority
import app.still.launcher.data.productivity.Task
import app.still.launcher.productivity.HabitEntry
import app.still.launcher.productivity.TaskEntry
import app.still.launcher.productivity.TodayAgenda
import app.still.launcher.schedule.Occurrence
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

// 2026-10-23 is a Friday.
class TaskGatesTest {

    private val today = LocalDate.parse("2026-10-23")
    private val noon = today.atTime(12, 0)
    private val zone = ZoneOffset.UTC

    private fun task(id: Long, priority: Priority, title: String = "Task $id") =
        Task(id = id, title = title, priority = priority, schedule = Schedule(start = today))

    private fun dueToday(task: Task, time: LocalTime? = null, done: Boolean = false) =
        TaskEntry(task, Occurrence(today, time), isDone = done)

    private fun overdue(task: Task) = TaskEntry(task, Occurrence(today.minusDays(2)), isDone = false, isOverdue = true)

    private fun undated(task: Task) = TaskEntry(task.copy(schedule = null), occurrence = null, isDone = false)

    private fun habit(id: Long, priority: Priority, done: Boolean = false) = HabitEntry(
        habit = Habit(id = id, title = "Habit $id", priority = priority, schedule = Schedule(today, Recurrence.Daily())),
        occurrence = Occurrence(today),
        progress = if (done) 1.0 else 0.0,
        isSkipped = false,
        quotaDone = null,
        isPending = !done,
    )

    private fun agenda(
        overdue: List<TaskEntry> = emptyList(),
        tasks: List<TaskEntry> = emptyList(),
        habits: List<HabitEntry> = emptyList(),
        undated: List<TaskEntry> = emptyList(),
    ) = TodayAgenda(today, overdue = overdue, today = tasks, habits = habits, undated = undated)

    private fun gate(
        id: Long = 1,
        minPriority: Priority = Priority.HIGH,
        packages: Set<String> = setOf("insta"),
        includeTasks: Boolean = true,
        includeHabits: Boolean = true,
        includeUndated: Boolean = false,
        window: GateWindow? = null,
        enabled: Boolean = true,
    ) = TaskGate(
        id = id,
        name = "Gate $id",
        packages = packages,
        minPriority = minPriority,
        includeTasks = includeTasks,
        includeHabits = includeHabits,
        includeUndated = includeUndated,
        window = window,
        enabled = enabled,
    )

    private fun itemsGate(items: List<GateItem>, requireAll: Boolean) =
        gate().copy(condition = GateCondition.ITEMS, items = items, requireAll = requireAll)

    @Test
    fun `a pending high-priority task closes a high-priority gate until it's done`() {
        val study = task(1, Priority.HIGH)
        assertTrue(gate().check(agenda(tasks = listOf(dueToday(study))), noon).isClosed)
        assertFalse(gate().check(agenda(tasks = listOf(dueToday(study, done = true))), noon).isClosed)
    }

    @Test
    fun `lower priorities don't count, and items without a priority never do`() {
        val items = agenda(tasks = listOf(dueToday(task(1, Priority.MEDIUM)), dueToday(task(2, Priority.NONE))))
        assertFalse(gate(minPriority = Priority.HIGH).check(items, noon).isClosed)
        assertEquals(1, gate(minPriority = Priority.MEDIUM).check(items, noon).pendingCount)
        assertEquals(1, gate(minPriority = Priority.LOW).check(items, noon).pendingCount)
    }

    @Test
    fun `a timed task only counts from its time`() {
        val items = agenda(tasks = listOf(dueToday(task(1, Priority.HIGH), time = LocalTime.of(18, 0))))
        assertFalse(gate().check(items, noon).isClosed)
        assertTrue(gate().check(items, today.atTime(18, 0)).isClosed)
    }

    @Test
    fun `overdue tasks count`() {
        assertTrue(gate().check(agenda(overdue = listOf(overdue(task(1, Priority.HIGH)))), noon).isClosed)
    }

    @Test
    fun `undated tasks count only when the gate includes them`() {
        val items = agenda(undated = listOf(undated(task(1, Priority.HIGH))))
        assertFalse(gate().check(items, noon).isClosed)
        assertTrue(gate(includeUndated = true).check(items, noon).isClosed)
    }

    @Test
    fun `a gate can wait for tasks only or habits only`() {
        val items = agenda(tasks = listOf(dueToday(task(1, Priority.HIGH))), habits = listOf(habit(2, Priority.HIGH)))
        assertEquals(1, gate(includeHabits = false).check(items, noon).tasks.size)
        assertEquals(0, gate(includeHabits = false).check(items, noon).habits.size)
        assertEquals(1, gate(includeTasks = false).check(items, noon).habits.size)
        assertEquals(0, gate(includeTasks = false).check(items, noon).tasks.size)
    }

    @Test
    fun `a gate for chosen items waits for all of them`() {
        val gym = habit(2, Priority.NONE)
        val read = dueToday(task(1, Priority.NONE))
        val other = dueToday(task(3, Priority.HIGH))
        val chosen = listOf(GateItem(ItemType.TASK, 1), GateItem(ItemType.HABIT, 2))
        val check = itemsGate(chosen, requireAll = true).check(agenda(tasks = listOf(read, other), habits = listOf(gym)), noon)
        assertEquals(listOf(read), check.tasks)
        assertEquals(listOf(gym), check.habits)

        val oneDone = agenda(tasks = listOf(dueToday(task(1, Priority.NONE), done = true)), habits = listOf(gym))
        assertTrue(itemsGate(chosen, requireAll = true).check(oneDone, noon).isClosed)
    }

    @Test
    fun `a gate for any one chosen item opens when one is done`() {
        val chosen = listOf(GateItem(ItemType.TASK, 1), GateItem(ItemType.HABIT, 2))
        val noneDone = agenda(tasks = listOf(dueToday(task(1, Priority.NONE))), habits = listOf(habit(2, Priority.NONE)))
        assertEquals(2, itemsGate(chosen, requireAll = false).check(noneDone, noon).pendingCount)
        val oneDone = agenda(tasks = listOf(dueToday(task(1, Priority.NONE))), habits = listOf(habit(2, Priority.NONE, done = true)))
        assertFalse(itemsGate(chosen, requireAll = false).check(oneDone, noon).isClosed)
    }

    @Test
    fun `chosen items that aren't due today don't keep the gate closed`() {
        val chosen = listOf(GateItem(ItemType.HABIT, 9))
        assertFalse(itemsGate(chosen, requireAll = true).check(agenda(), noon).isClosed)
    }

    @Test
    fun `a gate that's off or outside its hours stays open`() {
        val items = agenda(tasks = listOf(dueToday(task(1, Priority.HIGH))))
        assertFalse(gate(enabled = false).check(items, noon).isClosed)

        val weekdayMornings = GateWindow(DayOfWeek.entries.take(5).toSet(), 6 * 60, 11 * 60)
        assertFalse(gate(window = weekdayMornings).check(items, noon).isClosed)
        assertTrue(gate(window = weekdayMornings).check(items, today.atTime(10, 0)).isClosed)
        // Saturday morning is outside a weekday window.
        val saturday = agenda(overdue = listOf(overdue(task(1, Priority.HIGH))))
        assertFalse(gate(window = weekdayMornings).check(saturday, LocalDateTime.parse("2026-10-24T10:00")).isClosed)
    }

    @Test
    fun `an overnight window covers the next morning`() {
        val evenings = GateWindow(setOf(DayOfWeek.FRIDAY), 20 * 60, 2 * 60)
        val items = agenda(overdue = listOf(overdue(task(1, Priority.HIGH))))
        assertTrue(gate(window = evenings).check(items, LocalDateTime.parse("2026-10-24T01:00")).isClosed)
        assertFalse(gate(window = evenings).check(items, LocalDateTime.parse("2026-10-24T03:00")).isClosed)
    }

    @Test
    fun `closed gates list their apps, leaving out apps gates never close`() {
        val inputs = GateInputs(
            gates = listOf(gate(packages = setOf("insta", "maps"))),
            agenda = agenda(tasks = listOf(dueToday(task(1, Priority.HIGH)))),
            events = emptyList(),
        )
        val closed = closedGates(inputs, noon.toInstant(zone), zone) { it != "maps" }
        assertEquals(setOf("insta"), closed.keys)
    }

    @Test
    fun `an emergency open lets an app past that gate until it runs out`() {
        val now = noon.toInstant(zone)
        val opened = GateEvent(time = now, gateId = 1, packageName = "insta", type = GateEventType.EMERGENCY_OPEN, openUntil = now.plusSeconds(300))
        val inputs = GateInputs(
            gates = listOf(gate(id = 1), gate(id = 2, packages = setOf("youtube"))),
            agenda = agenda(tasks = listOf(dueToday(task(1, Priority.HIGH)))),
            events = listOf(opened),
        )
        assertEquals(setOf("youtube"), closedGates(inputs, now.plusSeconds(60), zone) { true }.keys)
        assertEquals(setOf("insta", "youtube"), closedGates(inputs, now.plusSeconds(301), zone) { true }.keys)
    }

    @Test
    fun `emergency opens are counted per gate and day, and the strictest gate decides`() {
        val strict = gate(id = 1).copy(emergencyOpensPerDay = 1, emergencyDelaySeconds = 60, emergencyMinutes = 2)
        val loose = gate(id = 2).copy(emergencyOpensPerDay = 3, emergencyDelaySeconds = 30, emergencyMinutes = 10)
        val checks = listOf(GateCheck(strict, listOf(dueToday(task(1, Priority.HIGH)))), GateCheck(loose, listOf(dueToday(task(1, Priority.HIGH)))))

        assertEquals(EmergencyOpen(left = 1, delaySeconds = 60, minutes = 2), emergencyOpenFor(checks, emptyList(), today, zone))

        val usedToday = GateEvent(time = noon.toInstant(zone), gateId = 1, packageName = "x", type = GateEventType.EMERGENCY_OPEN)
        assertNull(emergencyOpenFor(checks, listOf(usedToday), today, zone))

        val usedYesterday = usedToday.copy(time = Instant.parse("2026-10-22T12:00:00Z"))
        assertEquals(1, emergencyOpenFor(checks, listOf(usedYesterday), today, zone)?.left)
    }

    @Test
    fun `no gates, no emergency open needed`() {
        assertNull(emergencyOpenFor(emptyList(), emptyList(), today, zone))
    }
}
