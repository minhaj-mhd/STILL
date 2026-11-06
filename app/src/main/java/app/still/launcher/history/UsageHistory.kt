package app.still.launcher.history

import android.content.Context
import app.still.launcher.data.HistoryRepository
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.limit.dayStart
import app.still.launcher.limit.limitDay
import app.still.launcher.util.EssentialApps
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Saves each finished day of phone use from Android's usage events before Android forgets them.
 * Days follow the daily limit's start (midnight or 4 AM). The first time, it saves as many past
 * days as Android still has; after that, each day once it's over.
 */
class UsageHistory(
    context: Context,
    private val screenTime: ScreenTimeRepository,
    private val preferences: LauncherPreferences,
    private val repository: HistoryRepository,
    private val essentialApps: EssentialApps,
) {
    private val ownPackage = context.packageName
    private val mutex = Mutex()

    /** The day everything before was saved on, so looking again the same day costs nothing. */
    @Volatile
    private var savedBefore: LocalDate? = null

    /** Saves the finished days not saved yet. Does nothing without Usage access; it tries again next time. */
    suspend fun catchUp(now: ZonedDateTime = ZonedDateTime.now()) = mutex.withLock {
        val limit = preferences.current().limit
        val dayStartHour = limit.dayStartHour
        val today = limitDay(now.toLocalDateTime(), dayStartHour)
        if (savedBefore == today) return@withLock
        val lastSaved = repository.lastDay()
        if (lastSaved == null || lastSaved < today.minusDays(1)) {
            fun startOf(day: LocalDate): Long = dayStart(day, dayStartHour).atZone(now.zone).toInstant().toEpochMilli()
            val events = screenTime.events(startOf(today.minusDays(LOOKBACK_DAYS)), now.toInstant().toEpochMilli()) ?: return@withLock
            val earliest = events.minOfOrNull { it.timeMillis }
                ?.let { limitDay(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), now.zone), dayStartHour) }
            for (day in daysToSave(lastSaved, earliest, today)) {
                val usage = dayUsage(events, startOf(day), startOf(day.plusDays(1)), ownPackage, now.zone)
                repository.saveDay(day, dayStartHour, usage, limit.dayLimit(day, usage, ownPackage, essentialApps::alwaysOpenAfterLimit))
            }
        }
        savedBefore = today
    }

    /** Today so far, the way finished days are saved; null without Usage access. */
    suspend fun today(now: ZonedDateTime = ZonedDateTime.now()): HistoryDay? {
        val limit = preferences.current().limit
        val today = limitDay(now.toLocalDateTime(), limit.dayStartHour)
        val start = dayStart(today, limit.dayStartHour).atZone(now.zone).toInstant().toEpochMilli()
        val end = now.toInstant().toEpochMilli()
        // Read from a little earlier, so an app already open when the day began counts from then.
        val events = screenTime.events(start - TODAY_LOOK_BEHIND_MILLIS, end) ?: return null
        val usage = dayUsage(events, start, end, ownPackage, now.zone)
        return HistoryDay(today, usage.apps, usage.unlocks, usage.hourMillis, limit.dayLimit(today, usage, ownPackage, essentialApps::alwaysOpenAfterLimit))
    }

    private companion object {
        const val TODAY_LOOK_BEHIND_MILLIS = 6 * 60 * 60_000L
    }
}
