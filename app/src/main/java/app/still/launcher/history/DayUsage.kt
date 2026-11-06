package app.still.launcher.history

import app.still.launcher.blocking.AppUsageEvent
import app.still.launcher.blocking.ForegroundSpan
import app.still.launcher.blocking.foregroundSpans
import app.still.launcher.blocking.opensByApp
import app.still.launcher.limit.LimitSettings
import app.still.launcher.limit.usage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One app on one day: time in front and how many times it was opened. */
data class AppDay(val millis: Long, val opens: Int)

/**
 * A finished day of phone use, as STILL keeps it: Android forgets the details after about a week.
 * STILL's own pages are kept apart and never count as phone time.
 */
data class DayUsage(
    /** Every app but STILL. */
    val apps: Map<String, AppDay>,
    val stillMillis: Long,
    val unlocks: Int,
    /** Time in apps, STILL left out, in each clock hour from 0 to 23. */
    val hourMillis: List<Long>,
) {
    val screenMillis: Long get() = apps.values.sumOf { it.millis }
    val opens: Int get() = apps.values.sumOf { it.opens }
}

/** The day from [fromMillis] to [toMillis] in [events] (which may reach further either side), with [ownPackage] as STILL. */
fun dayUsage(events: List<AppUsageEvent>, fromMillis: Long, toMillis: Long, ownPackage: String, zone: ZoneId): DayUsage {
    val millis = HashMap<String, Long>()
    val hours = LongArray(HOURS)
    for (span in foregroundSpans(events, fromMillis, toMillis)) {
        millis[span.packageName] = (millis[span.packageName] ?: 0L) + span.millis
        if (span.packageName != ownPackage) addToHours(span, zone, hours)
    }
    val opens = opensByApp(events, fromMillis, toMillis)
    val apps = (millis.keys + opens.keys)
        .filter { it != ownPackage }
        .associateWith { AppDay(millis[it] ?: 0L, opens[it] ?: 0) }
    val unlocks = events.count { it.kind == AppUsageEvent.Kind.UNLOCK && it.timeMillis >= fromMillis && it.timeMillis < toMillis }
    return DayUsage(apps, millis[ownPackage] ?: 0L, unlocks, hours.toList())
}

/** What the daily limit counted on a day, and the limit that day in milliseconds (0 when it was off). */
data class DayLimit(val countedMillis: Long, val limitMillis: Long) {
    val isOver: Boolean get() = limitMillis > 0 && countedMillis >= limitMillis
}

/** [usage] on [day] measured the way the daily limit measures it, against that day's limit. */
fun LimitSettings.dayLimit(day: LocalDate, usage: DayUsage, ownPackage: String, alwaysOpen: (String) -> Boolean): DayLimit {
    // STILL's own time never counts, and isn't among the apps.
    val counted = usage(usage.apps.mapValues { it.value.millis }, ownPackage, alwaysOpen).counted.toMillis()
    return DayLimit(counted, planOn(day).minutesOn(day) * MILLIS_PER_MINUTE)
}

private const val MILLIS_PER_MINUTE = 60_000L

/** Splits [span] across the clock hours it covers. */
private fun addToHours(span: ForegroundSpan, zone: ZoneId, hours: LongArray) {
    var start = span.startMillis
    while (start < span.endMillis) {
        val time = Instant.ofEpochMilli(start).atZone(zone)
        val nextHour = time.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
        val end = minOf(nextHour, span.endMillis)
        hours[time.hour] += end - start
        start = end
    }
}

/**
 * The finished days to save, oldest first: each day after [lastSaved] up to the day before
 * [today]. Android's events begin partway into [earliestEventDay], so that day is never saved.
 * Nothing without any events, e.g. without Usage access.
 */
fun daysToSave(lastSaved: LocalDate?, earliestEventDay: LocalDate?, today: LocalDate): List<LocalDate> {
    val afterEarliest = earliestEventDay?.plusDays(1) ?: return emptyList()
    val first = lastSaved?.plusDays(1)?.let { maxOf(it, afterEarliest) } ?: afterEarliest
    return generateSequence(first) { it.plusDays(1) }.takeWhile { it < today }.toList()
}

const val HOURS = 24

/** How far back Android's events are read for the first save; it keeps roughly this long. */
const val LOOKBACK_DAYS = 10L
