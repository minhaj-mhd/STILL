package app.still.launcher.gates

import app.still.launcher.blocking.dailyWindowAt
import app.still.launcher.data.gates.GateCondition
import app.still.launcher.data.gates.GateEvent
import app.still.launcher.data.gates.GateEventType
import app.still.launcher.data.gates.GateItem
import app.still.launcher.data.gates.GateWindow
import app.still.launcher.data.gates.TaskGate
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.Priority
import app.still.launcher.productivity.HabitEntry
import app.still.launcher.productivity.TaskEntry
import app.still.launcher.productivity.TodayAgenda
import app.still.launcher.schedule.Occurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** One gate at one moment: the pending tasks and habits keeping it closed. None means it's open. */
data class GateCheck(
    val gate: TaskGate,
    val tasks: List<TaskEntry> = emptyList(),
    val habits: List<HabitEntry> = emptyList(),
) {
    val isClosed: Boolean get() = tasks.isNotEmpty() || habits.isNotEmpty()

    val pendingCount: Int get() = tasks.size + habits.size
}

/** An app's way past its closed gates today: how many opens are left, the wait, and how long it stays open. */
data class EmergencyOpen(val left: Int, val delaySeconds: Int, val minutes: Int)

/** Everything gates are decided from; the time is added when checking. */
data class GateInputs(
    val gates: List<TaskGate>,
    val agenda: TodayAgenda,
    /** Today's gate events. */
    val events: List<GateEvent>,
)

fun GateWindow.isActive(now: LocalDateTime): Boolean =
    dailyWindowAt(now, startMinute, endMinute) { it.dayOfWeek in days } != null

/** A gate applies while it's on and, if it has a window, inside it. */
fun TaskGate.appliesAt(now: LocalDateTime): Boolean = enabled && (window?.isActive(now) ?: true)

/**
 * What keeps this gate closed at [now], given today's [agenda]. Only items due by now count:
 * overdue ones, ones due today without a time, and ones whose time has come. Items with no
 * priority never count towards a priority gate.
 */
fun TaskGate.check(agenda: TodayAgenda, now: LocalDateTime): GateCheck {
    if (!appliesAt(now)) return GateCheck(this)
    val tasks = (agenda.overdue + agenda.today + agenda.undated).filter { it.occurrence.isDueBy(now) }
    val habits = agenda.habits.filter { it.occurrence.isDueBy(now) }
    return when (condition) {
        GateCondition.PRIORITY -> GateCheck(
            gate = this,
            tasks = if (!includeTasks) emptyList() else tasks.filter {
                it.isPending && it.task.priority.counts(minPriority) && (it.occurrence != null || includeUndated)
            },
            habits = if (!includeHabits) emptyList() else habits.filter { it.isPending && it.habit.priority.counts(minPriority) },
        )
        GateCondition.ITEMS -> {
            val chosenTasks = tasks.filter { GateItem(ItemType.TASK, it.task.id) in items }
            val chosenHabits = habits.filter { GateItem(ItemType.HABIT, it.habit.id) in items }
            // "Any one of them": one finished item opens the gate.
            val oneDone = chosenTasks.any { it.isDone } || chosenHabits.any { it.isDone }
            if (!requireAll && oneDone) GateCheck(this)
            else GateCheck(this, chosenTasks.filter { it.isPending }, chosenHabits.filter { it.isPending })
        }
    }
}

private fun Priority.counts(minimum: Priority): Boolean = this != Priority.NONE && this >= minimum

/** Undated tasks are always due; a timed occurrence only from its time. */
private fun Occurrence?.isDueBy(now: LocalDateTime): Boolean {
    if (this == null) return true
    val time = time ?: return true
    return date.atTime(time) <= now
}

/**
 * Every app a closed gate keeps shut at [now], with the gates keeping it shut. [canGate] leaves
 * out apps that are never gated, and an app on an emergency open is let through that gate.
 */
fun closedGates(inputs: GateInputs, now: Instant, zone: ZoneId, canGate: (String) -> Boolean): Map<String, List<GateCheck>> {
    val local = LocalDateTime.ofInstant(now, zone)
    val closed = mutableMapOf<String, MutableList<GateCheck>>()
    for (gate in inputs.gates) {
        if (gate.packages.isEmpty()) continue
        val check = gate.check(inputs.agenda, local)
        if (!check.isClosed) continue
        for (packageName in gate.packages) {
            if (!canGate(packageName) || gate.isEmergencyOpen(packageName, inputs.events, now)) continue
            closed.getOrPut(packageName) { mutableListOf() }.add(check)
        }
    }
    return closed
}

private fun TaskGate.isEmergencyOpen(packageName: String, events: List<GateEvent>, now: Instant): Boolean = events.any {
    it.gateId == id && it.packageName == packageName && it.type == GateEventType.EMERGENCY_OPEN && it.openUntil?.isAfter(now) == true
}

/** Emergency opens [gate] has used on [today], for any of its apps. */
fun emergencyOpensUsed(gate: TaskGate, events: List<GateEvent>, today: LocalDate, zone: ZoneId): Int = events.count {
    it.gateId == gate.id && it.type == GateEventType.EMERGENCY_OPEN && it.time.atZone(zone).toLocalDate() == today
}

/**
 * Whether an app kept shut by [checks] can be opened in an emergency today: every gate involved
 * must have an open left. It waits as long as the strictest gate asks and stays open as briefly.
 */
fun emergencyOpenFor(checks: List<GateCheck>, events: List<GateEvent>, today: LocalDate, zone: ZoneId): EmergencyOpen? {
    if (checks.isEmpty()) return null
    val left = checks.minOf { it.gate.emergencyOpensPerDay - emergencyOpensUsed(it.gate, events, today, zone) }
    if (left <= 0) return null
    return EmergencyOpen(
        left = left,
        delaySeconds = checks.maxOf { it.gate.emergencyDelaySeconds },
        minutes = checks.minOf { it.gate.emergencyMinutes },
    )
}
