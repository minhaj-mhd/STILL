package app.still.launcher.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.still.launcher.MainActivity
import app.still.launcher.R
import app.still.launcher.StillApplication
import app.still.launcher.data.BlockingRepository
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.precision.Intervention
import app.still.launcher.precision.InterventionKind
import app.still.launcher.precision.PrecisionMode
import kotlinx.coroutines.launch
import java.time.Instant

/** A planned stretch in one app, started from the "How long?" sheet. */
data class AppSession(
    val packageName: String,
    val label: String,
    val startMillis: Long,
    val plannedMinutes: Int,
) {
    val endMillis: Long get() = startMillis + plannedMinutes * 60_000L
}

/**
 * In-app time reminders. Starting a session sets two alarms: one a minute before the end and
 * one at the end. When they fire, the reminder checks (with Precision mode or Usage access)
 * whether you're still in that app, and stays quiet if you've already left. At the end it either
 * just reminds you or, if Settings says so, blocks the app for a few minutes. With Precision
 * mode STILL then steps in front of the app; without it, a notification's "Leave" button takes
 * you home.
 */
class AppTimeReminders(
    context: Context,
    private val blocking: BlockingRepository,
    private val screenTime: ScreenTimeRepository,
    private val preferences: LauncherPreferences,
    private val precision: PrecisionMode,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(appContext)

    fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(appContext.getString(R.string.time_reminder_channel_name))
            .setDescription(appContext.getString(R.string.time_reminder_channel_description))
            .build()
        notifications.createNotificationChannel(channel)
    }

    /** Starts a session for an app that's about to open. A new session replaces the app's previous one. */
    fun start(packageName: String, label: String, minutes: Int) {
        val session = AppSession(packageName, label, System.currentTimeMillis(), minutes)
        // With Precision mode, stepping out and back in during the session doesn't ask again.
        precision.gate.allow(packageName, session.endMillis)
        notifications.cancel(notificationId(session))
        if (minutes > 1) setAlarm(session.endMillis - 60_000L, alarmIntent(ACTION_ONE_MINUTE_LEFT, session))
        setAlarm(session.endMillis, alarmIntent(ACTION_TIME_UP, session))
    }

    suspend fun onOneMinuteLeft(session: AppSession) {
        if (isStillIn(session) == false) return
        notify(session, appContext.getString(R.string.time_reminder_one_minute, session.label), text = null)
    }

    suspend fun onTimeUp(session: AppSession) {
        // From now on, coming back to the app asks again.
        precision.gate.onTimeUp(session.packageName)
        if (isStillIn(session) == false) return
        val blockMinutes = preferences.current().timeUpBlockMinutes
        if (blockMinutes > 0) blocking.block(session.packageName, Instant.now().plusSeconds(blockMinutes * 60L))
        if (precision.isRunning) {
            val kind = if (blockMinutes > 0) InterventionKind.BLOCKED else InterventionKind.TIME_UP
            precision.intervene(Intervention(session.packageName, kind, session.plannedMinutes))
            return
        }
        val text = if (blockMinutes > 0) {
            appContext.getString(R.string.time_reminder_blocked, session.label, blockMinutes)
        } else {
            appContext.getString(R.string.time_reminder_planned, session.plannedMinutes)
        }
        notify(session, appContext.getString(R.string.time_reminder_time_up, session.label), text)
        if (blockMinutes > 0) bringHomeToFront()
    }

    /**
     * Whether you're still in the session's app: known with Precision mode, read from usage
     * events with Usage access, and null without either, in which case reminders always show.
     */
    private suspend fun isStillIn(session: AppSession): Boolean? {
        precision.appInFront()?.let { return it == session.packageName }
        if (!screenTime.hasAccess()) return null
        return screenTime.appInFront(session.startMillis) == session.packageName
    }

    /**
     * Without Precision mode, tries to bring STILL's home to the front so the blocked app closes.
     * Android may refuse to let an app open itself from the background; the notification's
     * "Leave" button still works.
     */
    private fun bringHomeToFront() {
        try {
            appContext.startActivity(homeIntent())
        } catch (e: ActivityNotFoundException) {
            // Nothing to do; the notification remains.
        }
    }

    private fun notify(session: AppSession, title: String, text: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val leave = PendingIntent.getActivity(appContext, 0, homeIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(leave)
            .addAction(0, appContext.getString(R.string.time_reminder_leave, session.label), leave)
            .build()
        notifications.notify(notificationId(session), notification)
    }

    private fun homeIntent(): Intent = Intent(appContext, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_GO_HOME, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun setAlarm(atMillis: Long, operation: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
    }

    /** One pending intent per action and app, so a new session for the app replaces the old alarms. */
    private fun alarmIntent(action: String, session: AppSession): PendingIntent {
        val intent = Intent(appContext, AppTimeReminderReceiver::class.java)
            .setAction(action)
            .setData("still://time-reminder/${session.packageName}".toUri())
            .putExtra(EXTRA_PACKAGE, session.packageName)
            .putExtra(EXTRA_LABEL, session.label)
            .putExtra(EXTRA_START, session.startMillis)
            .putExtra(EXTRA_MINUTES, session.plannedMinutes)
        return PendingIntent.getBroadcast(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun notificationId(session: AppSession): Int = "time:${session.packageName}".hashCode()

    companion object {
        const val CHANNEL_ID = "time_reminders"
        const val ACTION_ONE_MINUTE_LEFT = "app.still.launcher.reminders.ONE_MINUTE_LEFT"
        const val ACTION_TIME_UP = "app.still.launcher.reminders.TIME_UP"
        internal const val EXTRA_PACKAGE = "package"
        internal const val EXTRA_LABEL = "label"
        internal const val EXTRA_START = "start"
        internal const val EXTRA_MINUTES = "minutes"
    }
}

/** Fires the time reminders' alarms. Not exported: only STILL's own pending intents reach it. */
class AppTimeReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as StillApplication).container
        val session = AppSession(
            packageName = intent.getStringExtra(AppTimeReminders.EXTRA_PACKAGE) ?: return,
            label = intent.getStringExtra(AppTimeReminders.EXTRA_LABEL).orEmpty(),
            startMillis = intent.getLongExtra(AppTimeReminders.EXTRA_START, 0L),
            plannedMinutes = intent.getIntExtra(AppTimeReminders.EXTRA_MINUTES, 0),
        )
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (intent.action) {
                    AppTimeReminders.ACTION_ONE_MINUTE_LEFT -> container.appTimeReminders.onOneMinuteLeft(session)
                    AppTimeReminders.ACTION_TIME_UP -> container.appTimeReminders.onTimeUp(session)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
