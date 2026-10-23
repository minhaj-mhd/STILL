package app.still.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import app.still.launcher.blocking.AppUsageEvent
import app.still.launcher.blocking.foregroundAppAfter
import app.still.launcher.blocking.foregroundTimeByApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Time spent in one app today and over the last seven days (today included). */
data class AppScreenTime(val today: Duration, val lastSevenDays: Duration)

/** Time per package today and over the last seven days (today included). STILL itself is left out. */
data class ScreenTimeSummary(val today: Map<String, Duration>, val lastSevenDays: Map<String, Duration>)

/** Reads screen time from Android's usage statistics, which needs the "Usage access" permission. */
class ScreenTimeRepository(context: Context) {

    private val appContext = context.applicationContext
    private val usageStats = appContext.getSystemService(UsageStatsManager::class.java)
    private val appOps = appContext.getSystemService(AppOpsManager::class.java)

    /** Whether the user has turned on Usage access for STILL in system settings. */
    fun hasAccess(): Boolean {
        // checkOpNoThrow exists on every supported version and is the recommended call again on Android 17.
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Screen time for [packageName], or null without Usage access. */
    suspend fun screenTime(packageName: String, now: ZonedDateTime = ZonedDateTime.now()): AppScreenTime? {
        if (!hasAccess()) return null
        return withContext(Dispatchers.IO) {
            val today = now.toLocalDate()
            val weekStart = startOf(today.minusDays(6), now.zone)
            val todayStart = startOf(today, now.zone)
            val end = now.toInstant().toEpochMilli()
            val events = readEvents(weekStart, end).filter { it.packageName == packageName || it.kind == AppUsageEvent.Kind.SCREEN_OFF }
            AppScreenTime(
                today = Duration.ofMillis(foregroundTimeByApp(events, todayStart, end)[packageName] ?: 0L),
                lastSevenDays = Duration.ofMillis(foregroundTimeByApp(events, weekStart, end)[packageName] ?: 0L),
            )
        }
    }

    /** Screen time for every app, or null without Usage access. */
    suspend fun summary(now: ZonedDateTime = ZonedDateTime.now()): ScreenTimeSummary? {
        if (!hasAccess()) return null
        return withContext(Dispatchers.IO) {
            val today = now.toLocalDate()
            val weekStart = startOf(today.minusDays(6), now.zone)
            val end = now.toInstant().toEpochMilli()
            val events = readEvents(weekStart, end)
            fun byApp(from: Long) = foregroundTimeByApp(events, from, end)
                .filterKeys { it != appContext.packageName }
                .mapValues { Duration.ofMillis(it.value) }
            ScreenTimeSummary(today = byApp(startOf(today, now.zone)), lastSevenDays = byApp(weekStart))
        }
    }

    /** Milliseconds each app has been in front since [fromMillis], or null without Usage access. STILL is left out. */
    suspend fun foregroundMillisSince(fromMillis: Long): Map<String, Long>? {
        if (!hasAccess()) return null
        return withContext(Dispatchers.IO) {
            val end = System.currentTimeMillis()
            foregroundTimeByApp(readEvents(fromMillis, end), fromMillis, end).filterKeys { it != appContext.packageName }
        }
    }

    /**
     * The package in front now, judging by usage events since [sinceMillis]; null when nothing
     * is (screen off, or back on STILL counts as STILL). Requires [hasAccess].
     */
    suspend fun appInFront(sinceMillis: Long): String? = withContext(Dispatchers.IO) {
        foregroundAppAfter(readEvents(sinceMillis, System.currentTimeMillis()))
    }

    private fun readEvents(fromMillis: Long, toMillis: Long): List<AppUsageEvent> {
        val events = usageStats.queryEvents(fromMillis, toMillis)
        val event = UsageEvents.Event()
        val result = mutableListOf<AppUsageEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> AppUsageEvent.Kind.FOREGROUND
                UsageEvents.Event.ACTIVITY_PAUSED -> AppUsageEvent.Kind.BACKGROUND
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> AppUsageEvent.Kind.SCREEN_OFF
                else -> null
            } ?: continue
            result += AppUsageEvent(event.packageName, event.timeStamp, kind)
        }
        return result
    }

    private fun startOf(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}
