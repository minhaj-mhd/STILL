package app.still.launcher.reminders

import android.Manifest
import android.app.PendingIntent
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
import app.still.launcher.productivity.Reminder
import java.time.ZoneId

/** Posts and clears reminder notifications. */
class ReminderNotifier(private val context: Context) {

    private val notifications = NotificationManagerCompat.from(context)

    fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.reminder_channel_name))
            .setDescription(context.getString(R.string.reminder_channel_description))
            .build()
        notifications.createNotificationChannel(channel)
    }

    /** True when reminders can actually appear: permission granted (Android 13+) and not blocked in settings. */
    fun canNotify(): Boolean =
        notifications.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun show(reminder: Reminder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val dueAt = reminder.at.atZone(ZoneId.systemDefault()).toInstant()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(context.getString(R.string.reminder_due_at, TimeFormats.format(context, dueAt)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(dueAt.toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openTodayIntent())
        if (reminder.canMarkDone) {
            builder.addAction(0, context.getString(R.string.reminder_action_done), actionIntent(ReminderReceiver.ACTION_DONE, reminder))
        }
        builder.addAction(0, context.getString(R.string.reminder_action_snooze), actionIntent(ReminderReceiver.ACTION_SNOOZE, reminder))
        notifications.notify(reminder.notificationId, builder.build())
    }

    fun cancel(reminder: Reminder) {
        notifications.cancel(reminder.notificationId)
    }

    private fun openTodayIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(action: String, reminder: Reminder): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            reminderIntent(context, action, reminder),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val CHANNEL_ID = "reminders"
    }
}

private val Reminder.notificationId: Int get() = key.hashCode()
