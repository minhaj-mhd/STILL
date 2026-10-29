package app.still.launcher.productivity

import app.still.launcher.data.productivity.Plan
import app.still.launcher.data.productivity.PlanColor
import app.still.launcher.data.productivity.PlanPause
import app.still.launcher.data.productivity.Task
import app.still.launcher.schedule.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The dates a plan's days can fall on: from [start], on the plan's weekdays, outside its pauses.
 * A pause that hasn't ended closes every date from its start.
 */
class PlanCalendar(val start: LocalDate, weekdays: Int, private val pauses: List<PlanPause> = emptyList()) {

    /** No weekdays at all would leave nowhere to go, so it means every day. */
    private val weekdays = weekdays.takeIf { it and Plan.EVERY_DAY != 0 } ?: Plan.EVERY_DAY
    private val closedFrom: LocalDate? = pauses.lastOrNull()?.takeIf { it.until == null }?.from
    private val open = ArrayList<LocalDate>()

    constructor(plan: Plan, start: LocalDate) : this(start, plan.weekdays, plan.pauses)

    fun isOpen(date: LocalDate): Boolean =
        date >= start && (closedFrom == null || date < closedFrom) &&
            weekdays and (1 shl date.dayOfWeek.ordinal) != 0 && pauses.none { date in it }

    /** The [n]th open date, from 1; null when a pause that hasn't ended comes first. */
    fun date(n: Int): LocalDate? {
        while (open.size < n) {
            var next = open.lastOrNull()?.plusDays(1) ?: start
            while (!isOpen(next)) {
                if (closedFrom != null && next >= closedFrom) return null
                next = next.plusDays(1)
            }
            open += next
        }
        return open[n - 1]
    }

    /** How many open dates come before [date], so the first one on or after it is number this plus one. */
    fun countBefore(date: LocalDate): Int {
        var count = 0
        while (true) {
            val next = date(count + 1) ?: return count
            if (next >= date) return count
            count++
        }
    }
}

/** One day of a plan as it stands. */
data class PlanDay(
    val number: Int,
    /** Its tasks as stored, oldest first. */
    val tasks: List<Task>,
    /** The date it's due: moved along when the plan has slipped. Null while the plan is paused. */
    val due: LocalDate?,
    /** When its last task was done, once all of them are. */
    val finishedOn: LocalDate?,
) {
    val isFinished: Boolean get() = finishedOn != null

    /** Finished after the date it was due on. */
    val isLate: Boolean get() = finishedOn != null && due != null && finishedOn > due
}

enum class PlanState { NOT_STARTED, ACTIVE, PAUSED, FINISHED }

/** Where a plan stands on [today]: its days, and what its page says about them. */
data class PlanProgress(
    val plan: Plan,
    val days: List<PlanDay>,
    val today: LocalDate,
    /**
     * How many plan days it has moved along by; for a plan that keeps its dates, how many days are
     * overdue.
     */
    val behind: Int,
    /** The last day's date had nothing slipped, pauses aside. Null while paused. */
    val plannedEnd: LocalDate?,
    /** The last day's date as things stand, or when it was finished. Null while paused. */
    val end: LocalDate?,
) {
    /** The highest day number; days without tasks in between are rest days. */
    val length: Int get() = days.lastOrNull()?.number ?: 0

    /** The first day not finished yet. */
    val current: PlanDay? get() = days.firstOrNull { !it.isFinished }
    val daysDone: Int get() = days.count { it.isFinished }
    val daysOnTime: Int get() = days.count { it.isFinished && !it.isLate }
    val tasksDone: Int get() = days.sumOf { day -> day.tasks.count { it.completedAt != null } }
    val taskCount: Int get() = days.sumOf { it.tasks.size }

    val state: PlanState get() {
        val current = current ?: return PlanState.FINISHED
        return when {
            plan.isPaused -> PlanState.PAUSED
            tasksDone == 0 && current.due != null && current.due > today -> PlanState.NOT_STARTED
            else -> PlanState.ACTIVE
        }
    }
}

/**
 * Works out each day of [plan] from its [tasks] on [today].
 *
 * Days fall on the plan's open dates in order (see [PlanCalendar]), keeping any rest days between
 * them. When the plan moves along, a day not finished by its date stays due today rather than
 * going overdue, and the days after it move to the open dates after today; a day finished late
 * moves the later days by as much; and finishing early catches up days the plan had slipped by,
 * though never earlier than first planned. A day only counts as finished once all its tasks are.
 * When the plan keeps its dates, every day stays on its own date and goes overdue if missed.
 */
fun planProgress(plan: Plan, tasks: List<Task>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): PlanProgress {
    val own = tasks.filter { it.planId == plan.id && it.isPlanned }
    val calendar = PlanCalendar(plan, plan.start ?: plannedStart(own) ?: today)
    var shift = 0
    var settled = true
    var overdue = 0
    val days = own.groupBy { it.planDay!! }.toSortedMap().map { (number, dayTasks) ->
        val slot = calendar.date(number + shift)
        val finishedOn = dayTasks.takeIf { day -> day.all { it.completedAt != null } }
            ?.maxOf { it.completedAt!!.atZone(zone).toLocalDate() }
        when {
            !plan.movesAlong -> {
                if (finishedOn == null && slot != null && slot < today) overdue++
                PlanDay(number, dayTasks, slot.takeUnless { finishedOn == null && plan.isPaused }, finishedOn)
            }
            finishedOn != null -> {
                if (settled) shift = maxOf(0, calendar.countBefore(finishedOn.plusDays(1)) - number)
                PlanDay(number, dayTasks, slot, finishedOn)
            }
            else -> {
                val due = slot?.let { maxOf(it, today) }?.takeUnless { plan.isPaused }
                if (settled && due != null) shift = maxOf(shift, calendar.countBefore(due.plusDays(1)) - number)
                settled = false
                PlanDay(number, dayTasks, due, finishedOn = null)
            }
        }
    }
    val last = days.lastOrNull()
    return PlanProgress(
        plan = plan,
        days = days,
        today = today,
        behind = if (plan.movesAlong) shift else overdue,
        plannedEnd = last?.let { calendar.date(it.number) }?.takeUnless { plan.isPaused },
        end = last?.let { it.finishedOn ?: it.due },
    )
}

/**
 * Plan tasks on the days they're due now (see [planProgress]), and without the unfinished tasks
 * of paused plans, which wait with their plan. Subtasks, tasks done, and every other task are
 * returned as they are. [plans] gives each plan's settings; a plan missing from it moves along
 * every day.
 */
fun List<Task>.withPlanDates(plans: List<Plan>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Task> {
    val planIds = filter { it.isPlanned }.mapNotNullTo(HashSet()) { it.planId }
    if (planIds.isEmpty()) return this
    val byId = plans.associateBy { it.id }
    val due = HashMap<Long, LocalDate?>()
    planIds.forEach { id ->
        planProgress(byId[id] ?: Plan(id = id, name = ""), this, today, zone).days.forEach { day ->
            day.tasks.filter { it.completedAt == null }.forEach { due[it.id] = day.due }
        }
    }
    return mapNotNull { task ->
        if (task.id !in due) return@mapNotNull task
        val date = due[task.id] ?: return@mapNotNull null
        if (date == task.schedule!!.start) task else task.copy(schedule = task.schedule.copy(start = date))
    }
}

private val Task.isPlanned: Boolean
    get() = planId != null && planDay != null && parentId == null && !archived && schedule?.isRecurring == false

/** Day 1's date for a plan added before plans had a start: each task was planned that many days after it. */
private fun plannedStart(tasks: List<Task>): LocalDate? = tasks.minOfOrNull { it.schedule!!.start.minusDays(it.planDay!! - 1L) }

/** Where a task sits in its plan, for "Exam week · day 2 of 14". [name] is empty when the plan has none. */
data class PlanPlace(val name: String, val day: Int, val days: Int, val color: PlanColor = PlanColor.TEAL)

/**
 * A pasted list as a new plan's tasks. Each item goes on its numbered day, or on the first plan
 * day on or after the day it names; items without a day go on day 1. Habits aren't part of plans
 * and are left out, and a repeating item is a task on its first day only.
 */
fun PastedList.planTasks(calendar: PlanCalendar, today: LocalDate, createdAt: Instant): List<Pair<Task, List<Task>>> =
    items.mapIndexedNotNull { index, item ->
        if (item.isHabit) return@mapIndexedNotNull null
        // Spaced so each task's subtasks fit between it and the next, keeping the pasted order.
        val resolved = item.resolve(calendar.start, today, datelessOnStart = true, createdAt.plusMillis(index * (MAX_PASTED_SUBTASKS + 1L)))
        val task = (resolved as ResolvedItem.NewTask).task
        val day = item.planDayOn(calendar, today)
        val schedule = Schedule(start = calendar.date(day) ?: calendar.start, times = task.schedule?.times.orEmpty())
        task.copy(planDay = day, schedule = schedule) to resolved.subtasks
    }

/** The plan day [this] item goes on; see [planTasks]. */
fun PastedItem.planDayOn(calendar: PlanCalendar, today: LocalDate): Int = when (val named = day) {
    null -> 1
    is PastedDay.ListDay -> named.number
    else -> calendar.countBefore(dateOf(named, calendar.start, today)) + 1
}.coerceAtLeast(1)
