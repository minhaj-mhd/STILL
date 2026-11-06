package app.still.launcher.focus

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
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
import app.still.launcher.ui.components.TimeFormats
import java.time.Instant
import app.still.launcher.StillApplication
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.schedule.Occurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The focus timer for duration habits. One runs at a time and keeps running with STILL in the
 * background: it's saved, an alarm finishes it on time, and an ongoing notification counts down
 * with a Stop button. Finishing logs the minutes as done by the timer, the only completion STILL
 * can vouch for; stopping early logs the whole minutes so far as ordinary progress.
 */
class FocusTimer(
    context: Context,
    private val preferences: LauncherPreferences,
    private val productivity: ProductivityRepository,
    scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(appContext)

    // Start, stop and finish run one at a time, so a stop and the end alarm can't both log.
    private val mutex = Mutex()

    /** The running timer, or null. */
    val session: StateFlow<FocusSession?> = preferences.focusSession.stateIn(scope, SharingStarted.Eagerly, null)

    fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(appContext.getString(R.string.focus_channel_name))
            .setDescription(appContext.getString(R.string.focus_channel_description))
            .build()
        notifications.createNotificationChannel(channel)
    }

    /** Starts a timer for what's left of [habit]'s [occurrence]. A running timer stops first, keeping its minutes. */
    suspend fun start(habit: Habit, occurrence: Occurrence, progress: Double) = mutex.withLock {
        stopLocked()
        val session = FocusSession(
            habitId = habit.id,
            title = habit.title,
            date = occurrence.date,
            slotMinute = OccurrenceRecord.slotMinuteOf(occurrence),
            startMillis = System.currentTimeMillis(),
            minutes = timerMinutes(habit.target, progress),
            startProgress = progress,
        )
        preferences.setFocusSession(session)
        arm(session)
    }

    /** Stops early; the whole minutes so far are logged as ordinary progress. */
    suspend fun stop() = mutex.withLock { stopLocked() }

    /** Finishes the running timer if its time is up. The end alarm, the launcher and a reboot all call this. */
    suspend fun finishIfDue() = mutex.withLock {
        val session = preferences.currentFocusSession() ?: return@withLock
        if (!session.isDue(System.currentTimeMillis())) return@withLock
        preferences.setFocusSession(null)
        val habit = productivity.getHabit(session.habitId)
        if (habit == null) {
            notifications.cancel(NOTIFICATION_ID)
            return@withLock
        }
        val logged = productivity.getHabitProgress(habit.id, session.occurrence)
        productivity.setHabitProgress(habit, session.occurrence, maxOf(logged, session.startProgress + session.minutes), viaTimer = true)
        notifyDone(session)
    }

    /** After a reboot or an update: finish a timer whose time passed, or set its alarm and notification again. */
    suspend fun restore() {
        val session = preferences.currentFocusSession() ?: return
        if (session.isDue(System.currentTimeMillis())) finishIfDue() else arm(session)
    }

    private suspend fun stopLocked() {
        val session = preferences.currentFocusSession() ?: return
        preferences.setFocusSession(null)
        alarmManager.cancel(endIntent())
        notifications.cancel(NOTIFICATION_ID)
        val done = session.minutesDone(System.currentTimeMillis())
        val habit = productivity.getHabit(session.habitId) ?: return
        if (done > 0) {
            val logged = productivity.getHabitProgress(habit.id, session.occurrence)
            productivity.setHabitProgress(habit, session.occurrence, maxOf(logged, session.startProgress + done))
        }
    }

    private fun arm(session: FocusSession) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.endMillis, endIntent())
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.endMillis, endIntent())
        val until = TimeFormats.format(appContext, Instant.ofEpochMilli(session.endMillis))
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.focus_notification_title, session.title))
            .setContentText(appContext.getString(R.string.focus_notification_text, until))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(session.endMillis)
            .setShowWhen(true)
            .setContentIntent(openTodayIntent())
            .addAction(0, appContext.getString(R.string.focus_stop), receiverIntent(ACTION_STOP))
            .build()
        post(notification)
    }

    private fun notifyDone(session: FocusSession) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.focus_done_title, session.title))
            .setContentText(appContext.getString(R.string.focus_done_text, session.minutes))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openTodayIntent())
            .build()
        post(notification)
    }

    /** Shows [notification] when notifications are allowed; the timer works the same without. */
    private fun post(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notifications.notify(NOTIFICATION_ID, notification)
    }

    private fun endIntent(): PendingIntent = receiverIntent(ACTION_END)

    private fun receiverIntent(action: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(appContext, FocusTimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openTodayIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(appContext, REQUEST_OPEN_TODAY, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_ID = "focus"
        const val ACTION_END = "app.still.launcher.focus.END"
        const val ACTION_STOP = "app.still.launcher.focus.STOP"
        private const val NOTIFICATION_ID = 0x0F0C
        private const val REQUEST_OPEN_TODAY = 1
    }
}

/** The focus timer's end alarm and its notification's Stop button. Not exported. */
class FocusTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as StillApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (intent.action) {
                    FocusTimer.ACTION_END -> container.focusTimer.finishIfDue()
                    FocusTimer.ACTION_STOP -> container.focusTimer.stop()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
