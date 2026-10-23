package app.still.launcher.limit

import app.still.launcher.schedule.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDate
import kotlin.math.ceil

/**
 * One app's own limits: a daily budget, the longest one visit can last, and a wait between
 * visits, in minutes (0 for none), and whether opening it asks now and then what it's for.
 */
@Serializable
data class AppLimit(
    val dailyMinutes: Int = 0,
    val visitMinutes: Int = 0,
    val waitMinutes: Int = 0,
    val askWhy: Boolean = false,
) {
    val isOn: Boolean get() = dailyMinutes > 0 || visitMinutes > 0 || waitMinutes > 0 || askWhy
}

/** A loosening that waits for its day, so a limit can't be lifted the moment it's reached. */
@Serializable
data class PendingAppLimit(val limit: AppLimit, @Serializable(with = LocalDateSerializer::class) val from: LocalDate)

/** One app's limits as saved: what's in force, a loosening waiting for tomorrow, and the day they were first set. */
@Serializable
data class AppLimitEntry(
    val limit: AppLimit = AppLimit(),
    val pending: PendingAppLimit? = null,
    /** The day the app got its limits. That day they can still be changed freely, so trying out values doesn't lock in the first. */
    @Serializable(with = LocalDateSerializer::class) val startedOn: LocalDate? = null,
) {
    /** The limits in force on [day]: a waiting change takes over from its day, or at once on the day they started. */
    fun limitOn(day: LocalDate): AppLimit = pending?.takeIf { day >= it.from || day == startedOn }?.limit ?: limit

    /** A change still waiting on [day], if any. */
    fun waitingOn(day: LocalDate): PendingAppLimit? = pending?.takeIf { day < it.from && day != startedOn }
}

/** Whether [new] lets the app be used more than [old] in any way. */
fun isLoosening(old: AppLimit, new: AppLimit): Boolean =
    loosens(old.dailyMinutes, new.dailyMinutes) ||
        loosens(old.visitMinutes, new.visitMinutes) ||
        new.waitMinutes < old.waitMinutes ||
        (old.askWhy && !new.askWhy)

/** A cap loosens when it goes up or goes away. */
private fun loosens(old: Int, new: Int): Boolean = old > 0 && (new == 0 || new > old)

/**
 * Saves [new] as one app's limits on [today]. Tightening counts at once and drops any waiting
 * change; loosening waits for tomorrow, except on the day the limits were first set. Returns
 * null when the app is left with no limits at all.
 */
fun AppLimitEntry?.withLimit(new: AppLimit, today: LocalDate): AppLimitEntry? {
    val inForce = this?.limitOn(today) ?: AppLimit()
    if (this != null && today != startedOn && isLoosening(inForce, new)) {
        return copy(limit = inForce, pending = PendingAppLimit(new, today.plusDays(1)))
    }
    if (!new.isOn) return null
    return AppLimitEntry(limit = new, startedOn = if (inForce.isOn) this?.startedOn else today)
}

/**
 * One stretch in an app, from when it came to the front to when it left: null while it's still
 * there, or while STILL can't tell. [timeUp] marks a visit ended by its length running out, so
 * coming straight back starts a new one.
 */
@Serializable
data class AppVisit(val startMillis: Long, val leftMillis: Long? = null, val timeUp: Boolean = false) {
    /** Still going at [nowMillis]: the app is in front, or left less than [graceMillis] ago and its time didn't run out. */
    fun isOngoing(nowMillis: Long, graceMillis: Long): Boolean =
        !timeUp && (leftMillis == null || nowMillis - leftMillis < graceMillis)
}

/** Why an app's own limits keep it closed. */
enum class AppClosed { DONE_TODAY, WAITING }

/** Where an app stands against its own limits at one moment. */
data class AppLimitStatus(
    val limit: AppLimit,
    val usedToday: Duration,
    /** When it opens again after the wait between visits; null when it isn't waiting. */
    val opensAtMillis: Long? = null,
) {
    /** What's left of the daily budget; null without one. */
    val left: Duration? get() = if (limit.dailyMinutes > 0) Duration.ofMinutes(limit.dailyMinutes.toLong()).minus(usedToday).coerceAtLeast(Duration.ZERO) else null

    val closed: AppClosed? get() = when {
        left?.isZero == true -> AppClosed.DONE_TODAY
        opensAtMillis != null -> AppClosed.WAITING
        else -> null
    }
}

/** [entry]'s status on [day], or null when no limit is in force then. */
fun appLimitStatus(entry: AppLimitEntry, day: LocalDate, usedMillis: Long, visit: AppVisit?, nowMillis: Long, graceMillis: Long): AppLimitStatus? {
    val limit = entry.limitOn(day).takeIf { it.isOn } ?: return null
    return AppLimitStatus(limit, Duration.ofMillis(usedMillis), opensAgainAt(limit, visit, nowMillis, graceMillis))
}

/** When the app opens again after [visit], or null when it isn't waiting at [nowMillis]. */
fun opensAgainAt(limit: AppLimit, visit: AppVisit?, nowMillis: Long, graceMillis: Long): Long? {
    if (limit.waitMinutes <= 0 || visit == null || visit.isOngoing(nowMillis, graceMillis)) return null
    val ended = visit.leftMillis ?: return null
    return (ended + limit.waitMinutes * MILLIS_PER_MINUTE).takeIf { it > nowMillis }
}

/**
 * How many minutes a new visit may last: the visit length or what's left of the day, whichever
 * is shorter (at least a minute); null when neither caps it.
 */
fun visitLength(limit: AppLimit, usedToday: Duration): Int? {
    val leftToday = if (limit.dailyMinutes > 0) {
        ceil((limit.dailyMinutes * MILLIS_PER_MINUTE - usedToday.toMillis()) / MILLIS_PER_MINUTE.toDouble()).toInt().coerceAtLeast(1)
    } else {
        null
    }
    return listOfNotNull(limit.visitMinutes.takeIf { it > 0 }, leftToday).minOrNull()
}

/** The words the pause uses: the usual question, a focus session going on, or the app opened again and again. */
enum class PauseContext { USUAL, FOCUS, OFTEN }

/**
 * Whether opening an app with [limit] asks what it's for, and how: always during a focus
 * session, when it's already been opened [OFTEN_OPENS] times in the last hour, and otherwise at
 * most once every [ASK_EVERY_MILLIS]. Null when it opens without asking.
 */
fun pauseContext(limit: AppLimit, focusing: Boolean, opensLastHour: Int, lastAskedMillis: Long?, nowMillis: Long): PauseContext? = when {
    !limit.askWhy -> null
    focusing -> PauseContext.FOCUS
    opensLastHour >= OFTEN_OPENS -> PauseContext.OFTEN
    lastAskedMillis == null || nowMillis - lastAskedMillis >= ASK_EVERY_MILLIS -> PauseContext.USUAL
    else -> null
}

const val OFTEN_OPENS = 3
const val ASK_EVERY_MILLIS = 2 * 60 * 60 * 1000L
const val HOUR_MILLIS = 60 * 60 * 1000L
private const val MILLIS_PER_MINUTE = 60_000L
