package app.still.launcher.limit

import app.still.launcher.schedule.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** How many minutes of phone time a day allows; 0 means no limit. Weekends can differ. */
@Serializable
data class LimitPlan(val weekdayMinutes: Int = 0, val weekendMinutes: Int? = null) {
    val isOn: Boolean get() = weekdayMinutes > 0

    fun minutesOn(day: LocalDate): Int =
        if (weekendMinutes != null && day.dayOfWeek in WEEKEND) weekendMinutes else weekdayMinutes

    private companion object {
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

/** A raise that waits for its day, so you can't lift the limit the moment you hit it. */
@Serializable
data class PendingPlan(val plan: LimitPlan, @Serializable(with = LocalDateSerializer::class) val from: LocalDate)

/** The emergency minutes used on [day], and the app let through until [untilMillis]. */
@Serializable
data class LimitEmergency(
    @Serializable(with = LocalDateSerializer::class) val day: LocalDate,
    val used: Int,
    val packageName: String? = null,
    val untilMillis: Long = 0,
)

/** The daily phone limit's settings, saved as one piece of JSON. */
@Serializable
data class LimitSettings(
    val plan: LimitPlan = LimitPlan(),
    val pending: PendingPlan? = null,
    val warnMinutes: Int = 15,
    /** The hour a day starts: 0 for midnight, 4 for 4 AM. */
    val dayStartHour: Int = 0,
    /** After the limit, only these open (essential apps always do). */
    val allowed: Set<String> = emptySet(),
    /** Whether time in allowed apps uses up the limit too. */
    val countsAllowed: Boolean = false,
    val raiseStartsTomorrow: Boolean = true,
    /** Switching [raiseStartsTomorrow] off is itself a raise, so it waits until this day. */
    @Serializable(with = LocalDateSerializer::class) val raiseRuleOffFrom: LocalDate? = null,
    val emergency: LimitEmergency? = null,
    /** The day the limit was switched on. That day it can still be set freely, so stepping up to the limit you want doesn't lock in the first value. */
    @Serializable(with = LocalDateSerializer::class) val startedOn: LocalDate? = null,
) {
    /** Whether raises wait for tomorrow on [day]. Never on the day the limit started. */
    fun raisesWait(day: LocalDate): Boolean =
        day != startedOn && (raiseStartsTomorrow || (raiseRuleOffFrom != null && day < raiseRuleOffFrom))

    /** The plan in force on [day]: a waiting raise takes over from its day, or at once on the day the limit started. */
    fun planOn(day: LocalDate): LimitPlan = pending?.takeIf { day >= it.from || day == startedOn }?.plan ?: plan

    fun emergenciesLeft(day: LocalDate): Int = EMERGENCIES_PER_DAY - (emergency?.takeIf { it.day == day }?.used ?: 0)

    /** Whether [packageName] is on its emergency minutes at [nowMillis]. */
    fun isOnEmergency(packageName: String, nowMillis: Long): Boolean =
        emergency?.let { it.packageName == packageName && nowMillis < it.untilMillis } == true

    /**
     * Whether the limit keeps [packageName] closed: the day's limit is used up, and the app is
     * neither [alwaysOpen], allowed, nor on its emergency minutes.
     */
    fun keepsClosed(state: LimitState?, packageName: String, nowMillis: Long, alwaysOpen: (String) -> Boolean): Boolean =
        state?.isReached == true && packageName !in allowed && !alwaysOpen(packageName) && !isOnEmergency(packageName, nowMillis)

    /** Uses one of [day]'s emergency openings for [packageName], until [untilMillis]. */
    fun withEmergency(day: LocalDate, packageName: String, untilMillis: Long): LimitSettings {
        val usedToday = emergency?.takeIf { it.day == day }?.used ?: 0
        return copy(emergency = LimitEmergency(day, usedToday + 1, packageName, untilMillis))
    }

    companion object {
        const val EMERGENCIES_PER_DAY = 2
        const val EMERGENCY_MINUTES = 5
        const val EMERGENCY_DELAY_SECONDS = 30
    }
}

/** The day [now] counts towards when days start at [dayStartHour]. */
fun limitDay(now: LocalDateTime, dayStartHour: Int): LocalDate = now.minusHours(dayStartHour.toLong()).toLocalDate()

/** When [day] starts. */
fun dayStart(day: LocalDate, dayStartHour: Int): LocalDateTime = day.atTime(dayStartHour, 0)

/** Whether [new] allows more phone time than [old] on any day. Switching the limit off is the biggest raise. */
fun isRaise(old: LimitPlan, new: LimitPlan): Boolean {
    if (!old.isOn) return false
    if (!new.isOn) return true
    val oldWeekend = old.weekendMinutes ?: old.weekdayMinutes
    val newWeekend = new.weekendMinutes ?: new.weekdayMinutes
    return new.weekdayMinutes > old.weekdayMinutes || newWeekend > oldWeekend
}

/**
 * Saves [new] as the plan on [today]. Lowering it, or setting a first limit, counts straight
 * away; a raise waits for tomorrow while [LimitSettings.raisesWait]. Any waiting raise is
 * replaced. Switching the limit on marks today as its start.
 */
fun LimitSettings.withPlan(new: LimitPlan, today: LocalDate): LimitSettings {
    val inForce = planOn(today)
    if (raisesWait(today) && isRaise(inForce, new)) {
        return copy(plan = inForce, pending = PendingPlan(new, today.plusDays(1)))
    }
    val started = when {
        !new.isOn -> null
        !inForce.isOn -> today
        else -> startedOn
    }
    return copy(plan = new, pending = null, startedOn = started)
}

/** Switching the raise rule off is a raise too, so it only lets go tomorrow. Switching it on counts now. */
fun LimitSettings.withRaiseRule(on: Boolean, today: LocalDate): LimitSettings =
    if (on) copy(raiseStartsTomorrow = true, raiseRuleOffFrom = null)
    else copy(raiseStartsTomorrow = false, raiseRuleOffFrom = if (raisesWait(today)) today.plusDays(1) else raiseRuleOffFrom)

/** Where the day stands against the limit. */
data class LimitState(
    val day: LocalDate,
    val limitMinutes: Int,
    val used: Duration,
    val warnMinutes: Int,
    val resetsAt: LocalDateTime,
    /** Time today that didn't use up the limit, by why; only what's there. */
    val uncounted: Map<Uncounted, Duration> = emptyMap(),
) {
    val isOn: Boolean get() = limitMinutes > 0
    val limit: Duration get() = Duration.ofMinutes(limitMinutes.toLong())
    val left: Duration get() = if (used >= limit) Duration.ZERO else limit.minus(used)
    val isReached: Boolean get() = isOn && used >= limit
    val isWarning: Boolean get() = isOn && !isReached && warnMinutes > 0 && left <= Duration.ofMinutes(warnMinutes.toLong())

    /** How much of the limit is used, 0 to 1. */
    val fraction: Float get() = if (!isOn) 0f else (used.toMillis().toFloat() / limit.toMillis()).coerceIn(0f, 1f)
}

/** Why time didn't use up the limit. */
enum class Uncounted {
    /** Allowed apps, while [LimitSettings.countsAllowed] is off. */
    ALLOWED,

    /** The phone app, Settings and the clock, which always open. */
    ALWAYS_OPEN,

    /** STILL's own pages. */
    STILL,
}

/** The day's time against the limit, and what was left out. */
data class LimitUsage(val counted: Duration, val uncounted: Map<Uncounted, Duration>)

/**
 * Splits per-app [millisByApp] into time that counts against the limit and time that doesn't.
 * [still] is STILL's own package; [alwaysOpen] names the apps that always open, STILL among them.
 */
fun LimitSettings.usage(millisByApp: Map<String, Long>, still: String, alwaysOpen: (String) -> Boolean): LimitUsage {
    var counted = 0L
    val uncounted = mutableMapOf<Uncounted, Long>()
    millisByApp.forEach { (packageName, millis) ->
        val why = when {
            packageName == still -> Uncounted.STILL
            alwaysOpen(packageName) -> Uncounted.ALWAYS_OPEN
            !countsAllowed && packageName in allowed -> Uncounted.ALLOWED
            else -> null
        }
        if (why == null) counted += millis else uncounted[why] = (uncounted[why] ?: 0L) + millis
    }
    return LimitUsage(Duration.ofMillis(counted), uncounted.filterValues { it > 0 }.mapValues { Duration.ofMillis(it.value) })
}
