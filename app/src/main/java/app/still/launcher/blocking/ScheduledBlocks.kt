package app.still.launcher.blocking

import app.still.launcher.data.blocking.BlockingSchedule
import app.still.launcher.schedule.occursOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A schedule's window that is running: it started on [startDate] and ends at [endsAt]. */
data class ActiveWindow(val startDate: LocalDate, val endsAt: LocalDateTime)

/** Why and until when a schedule blocks an app. */
data class ScheduledBlock(val until: LocalDateTime, val scheduleName: String)

val BlockingSchedule.isOvernight: Boolean get() = endMinute <= startMinute

/**
 * The window of this schedule running at [now], or null. A window belongs to the day it
 * starts, so an overnight window that began yesterday still counts this morning, and turning
 * a day off removes its whole window, including the part after midnight.
 */
fun BlockingSchedule.activeWindow(now: LocalDateTime): ActiveWindow? =
    dailyWindowAt(now, startMinute, endMinute) { schedule.occursOn(it) }

/**
 * The window from [startMinute] to [endMinute] running at [now], on the days [occursOn] accepts,
 * or null. An end at or before the start runs into the next morning, and that part belongs to
 * the day the window started.
 */
fun dailyWindowAt(now: LocalDateTime, startMinute: Int, endMinute: Int, occursOn: (LocalDate) -> Boolean): ActiveWindow? {
    val today = now.toLocalDate()
    val minute = now.hour * 60 + now.minute
    val end = LocalTime.of(endMinute / 60, endMinute % 60)
    val overnight = endMinute <= startMinute

    if (occursOn(today) && minute >= startMinute && (overnight || minute < endMinute)) {
        val endDate = if (overnight) today.plusDays(1) else today
        return ActiveWindow(today, endDate.atTime(end))
    }
    val yesterday = today.minusDays(1)
    if (overnight && occursOn(yesterday) && minute < endMinute) {
        return ActiveWindow(yesterday, today.atTime(end))
    }
    return null
}

/**
 * Every app that an enabled schedule is blocking at [now], with the latest end time among the
 * schedules that block it and that schedule's name.
 */
fun scheduledBlocks(schedules: List<BlockingSchedule>, now: LocalDateTime): Map<String, ScheduledBlock> {
    val blocks = mutableMapOf<String, ScheduledBlock>()
    for (schedule in schedules) {
        if (!schedule.enabled) continue
        val window = schedule.activeWindow(now) ?: continue
        for (packageName in schedule.packages) {
            val current = blocks[packageName]
            if (current == null || window.endsAt > current.until) {
                blocks[packageName] = ScheduledBlock(window.endsAt, schedule.name)
            }
        }
    }
    return blocks
}

/**
 * The schedule with its current window turned off, or today's if none is running: "off just
 * for today". The day's skip is kept in the schedule; older skips are dropped as they no
 * longer matter.
 */
fun BlockingSchedule.offForToday(now: LocalDateTime): BlockingSchedule {
    val day = activeWindow(now)?.startDate ?: now.toLocalDate()
    val keep = schedule.skippedDates.filter { it >= now.toLocalDate().minusDays(1) }.toSet()
    return copy(schedule = schedule.copy(skippedDates = keep + day))
}

/** Whether this schedule is turned off just for today (or for the window running now). */
fun BlockingSchedule.isOffForToday(now: LocalDateTime): Boolean {
    val today = now.toLocalDate()
    val yesterday = today.minusDays(1)
    // Last night's window is still running this morning if it ends later than now.
    val runningSinceYesterday = isOvernight && now.hour * 60 + now.minute < endMinute
    return today in schedule.skippedDates || (runningSinceYesterday && yesterday in schedule.skippedDates)
}

/** Turns a schedule back on after [offForToday]. */
fun BlockingSchedule.backOn(now: LocalDateTime): BlockingSchedule {
    val today = now.toLocalDate()
    return copy(schedule = schedule.copy(skippedDates = schedule.skippedDates - today - today.minusDays(1)))
}
