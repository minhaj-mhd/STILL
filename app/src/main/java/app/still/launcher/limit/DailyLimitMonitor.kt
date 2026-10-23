package app.still.launcher.limit

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.still.launcher.MainActivity
import app.still.launcher.R
import app.still.launcher.StillApplication
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.LauncherSettings
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.util.EssentialApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Measures the day's phone time against the daily limit, from Usage access. It looks again when
 * STILL is on screen, when Precision mode checks the app in front, and by alarm at the earliest
 * moment the warning or the limit could arrive, so it can warn and say time's up with STILL in
 * the background.
 */
class DailyLimitMonitor(
    context: Context,
    private val preferences: LauncherPreferences,
    private val screenTime: ScreenTimeRepository,
    private val essentialApps: EssentialApps,
    scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(appContext)
    private val mutex = Mutex()

    private val settings: StateFlow<LauncherSettings> = preferences.settings.stateIn(scope, SharingStarted.Eagerly, LauncherSettings())

    private val _state = MutableStateFlow<LimitState?>(null)

    /** Null while there's no limit, or no Usage access to measure it with. */
    val state: StateFlow<LimitState?> = _state.asStateFlow()

    init {
        scope.launch { preferences.settings.map { it.limit }.distinctUntilChanged().collect { refresh() } }
    }

    fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(appContext.getString(R.string.limit_channel_name))
            .setDescription(appContext.getString(R.string.limit_channel_description))
            .build()
        notifications.createNotificationChannel(channel)
    }

    /** Whether the limit keeps [packageName] closed right now. */
    fun keepsClosed(packageName: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        settings.value.limit.keepsClosed(_state.value, packageName, nowMillis, essentialApps::alwaysOpenAfterLimit)

    /** Measures the day so far and acts on it: warns, says time's up, and sets the next check. */
    suspend fun refresh() = mutex.withLock {
        val limit = preferences.current().limit
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val day = limitDay(now.toLocalDateTime(), limit.dayStartHour)
        val minutes = limit.planOn(day).minutesOn(day)
        val start = dayStart(day, limit.dayStartHour).atZone(zone).toInstant().toEpochMilli()
        val byApp = if (minutes > 0) screenTime.foregroundMillisSince(start) else null
        if (byApp == null) {
            _state.value = null
            alarmManager.cancel(checkIntent())
            return@withLock
        }
        val used = countedUsage(byApp) { packageName ->
            essentialApps.alwaysOpenAfterLimit(packageName) || (!limit.countsAllowed && packageName in limit.allowed)
        }
        val state = LimitState(day, minutes, used, limit.warnMinutes, dayStart(day.plusDays(1), limit.dayStartHour))
        _state.value = state
        notifyOnce(state)
        scheduleNextCheck(state, now)
    }

    /** Uses one of today's emergency openings on [packageName]; returns once the saved settings show it. */
    suspend fun useEmergency(packageName: String) {
        val day = _state.value?.day ?: return
        val until = System.currentTimeMillis() + Duration.ofMinutes(LimitSettings.EMERGENCY_MINUTES.toLong()).toMillis()
        preferences.updateLimit { it.withEmergency(day, packageName, until) }
        // Wait until the settings say so, so Precision mode doesn't close the app again as it opens.
        withTimeoutOrNull(SAVE_TIMEOUT_MILLIS) { settings.first { it.limit.isOnEmergency(packageName, System.currentTimeMillis()) } }
    }

    /** The warning and time's up are each shown once a day, even if STILL restarts in between. */
    private suspend fun notifyOnce(state: LimitState) {
        val level = when {
            state.isReached -> NOTICE_REACHED
            state.isWarning -> NOTICE_WARN
            else -> return
        }
        val notice = "${state.day}:$level"
        val last = preferences.limitNotice()
        if (last == notice || (level == NOTICE_WARN && last == "${state.day}:$NOTICE_REACHED")) return
        preferences.setLimitNotice(notice)
        val title = if (state.isReached) appContext.getString(R.string.limit_notice_reached)
        else appContext.getString(R.string.limit_notice_warn, state.left.toMinutes().coerceAtLeast(1))
        val text = if (state.isReached) appContext.getString(R.string.limit_notice_reached_text) else null
        val open = Intent(appContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_GO_HOME, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(appContext, REQUEST_OPEN, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notifications.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Time only runs while the phone is in use, so the earliest the warning or the limit can come
     * is now plus what's left. Looking again then is enough; after the limit, look at the next
     * day's start to clear it.
     */
    private fun scheduleNextCheck(state: LimitState, now: ZonedDateTime) {
        val next = when {
            state.isReached -> state.resetsAt.atZone(now.zone).toInstant().toEpochMilli()
            !state.isWarning && state.warnMinutes > 0 -> now.toInstant().toEpochMilli() + state.left.minusMinutes(state.warnMinutes.toLong()).toMillis()
            else -> now.toInstant().toEpochMilli() + state.left.toMillis()
        }.coerceAtLeast(now.toInstant().toEpochMilli() + MIN_CHECK_GAP_MILLIS)
        // Not a wake-up alarm: while the phone sleeps no time is used anyway.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact) alarmManager.setExact(AlarmManager.RTC, next, checkIntent()) else alarmManager.set(AlarmManager.RTC, next, checkIntent())
    }

    private fun checkIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(appContext, LimitCheckReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL_ID = "daily_limit"
        const val NOTIFICATION_ID = 0x11A1
        const val REQUEST_OPEN = 3
        const val NOTICE_WARN = "WARN"
        const val NOTICE_REACHED = "REACHED"
        const val MIN_CHECK_GAP_MILLIS = 30_000L
        const val SAVE_TIMEOUT_MILLIS = 2_000L
    }
}

/** The daily limit's check alarm. Not exported. */
class LimitCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as StillApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.dailyLimit.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
