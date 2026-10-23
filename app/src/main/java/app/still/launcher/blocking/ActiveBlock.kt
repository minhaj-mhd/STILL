package app.still.launcher.blocking

import java.time.Instant
import java.time.ZoneId

/** A running block on an app: until when, and the schedule behind it (null for a block set by hand). */
data class ActiveBlock(val until: Instant, val scheduleName: String?)

/**
 * The block on [packageName] at [now], from the blocks set by hand and what the schedules are
 * blocking ([scheduledBlocks]). The two can overlap; whichever ends later counts. Callers leave
 * out essential apps, which are never blocked.
 */
fun activeBlock(
    packageName: String,
    manualBlocks: Map<String, Instant>,
    scheduled: Map<String, ScheduledBlock>,
    now: Instant,
    zone: ZoneId,
): ActiveBlock? {
    val manualUntil = manualBlocks[packageName]?.takeIf { it.isAfter(now) }
    val bySchedule = scheduled[packageName]?.let { ActiveBlock(it.until.atZone(zone).toInstant(), it.scheduleName) }
    return when {
        bySchedule != null && (manualUntil == null || bySchedule.until > manualUntil) -> bySchedule
        manualUntil != null -> ActiveBlock(manualUntil, scheduleName = null)
        else -> null
    }
}
