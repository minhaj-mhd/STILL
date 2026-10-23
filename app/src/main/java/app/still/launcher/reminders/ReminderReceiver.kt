package app.still.launcher.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.still.launcher.StillApplication
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.productivity.Reminder
import app.still.launcher.schedule.Occurrence
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** STILL's own alarms and notification buttons. Not exported: only STILL's pending intents reach it. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as StillApplication).container
        val scheduler = container.reminderScheduler
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (intent.action) {
                    ACTION_CHECK -> scheduler.onCheckAlarm()
                    ACTION_DONE -> intent.toReminder()?.let { scheduler.markDone(it) }
                    ACTION_SNOOZE -> intent.toReminder()?.let { scheduler.snooze(it) }
                    ACTION_SNOOZE_ENDED -> intent.toReminder()?.let { scheduler.onSnoozeEnded(it) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "app.still.launcher.reminders.CHECK"
        const val ACTION_DONE = "app.still.launcher.reminders.DONE"
        const val ACTION_SNOOZE = "app.still.launcher.reminders.SNOOZE"
        const val ACTION_SNOOZE_ENDED = "app.still.launcher.reminders.SNOOZE_ENDED"
    }
}

/**
 * System events after which the next alarm must be set again: reboot, clock or time zone
 * changes, an app update, or the exact-alarm permission being granted. Exported for those
 * broadcasts only; anything else is ignored.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val container = (context.applicationContext as StillApplication).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.reminderScheduler.rescheduleNext()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        // The exact-alarm action is only a string constant; on Android < 12 it simply never arrives.
        @SuppressLint("InlinedApi")
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

/**
 * An explicit intent to [ReminderReceiver] carrying [reminder]. The data URI makes each
 * reminder's pending intents distinct, so one notification's buttons never act on another.
 */
internal fun reminderIntent(context: Context, action: String, reminder: Reminder): Intent =
    Intent(context, ReminderReceiver::class.java)
        .setAction(action)
        .setData("still://reminder/${reminder.key}".toUri())
        .putExtra(EXTRA_TYPE, reminder.itemType.name)
        .putExtra(EXTRA_ID, reminder.itemId)
        .putExtra(EXTRA_TITLE, reminder.title)
        .putExtra(EXTRA_DATE, reminder.occurrence.date.toString())
        .putExtra(EXTRA_TIME, reminder.occurrence.time?.toString())
        .putExtra(EXTRA_AT, reminder.at.toString())
        .putExtra(EXTRA_CAN_MARK_DONE, reminder.canMarkDone)

private fun Intent.toReminder(): Reminder? {
    val type = getStringExtra(EXTRA_TYPE)?.let { name -> ItemType.entries.firstOrNull { it.name == name } } ?: return null
    val date = getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return null
    val at = getStringExtra(EXTRA_AT)?.let(LocalDateTime::parse) ?: return null
    return Reminder(
        itemType = type,
        itemId = getLongExtra(EXTRA_ID, -1).takeIf { it >= 0 } ?: return null,
        title = getStringExtra(EXTRA_TITLE).orEmpty(),
        occurrence = Occurrence(date, getStringExtra(EXTRA_TIME)?.let(LocalTime::parse)),
        at = at,
        canMarkDone = getBooleanExtra(EXTRA_CAN_MARK_DONE, false),
    )
}

private const val EXTRA_TYPE = "type"
private const val EXTRA_ID = "id"
private const val EXTRA_TITLE = "title"
private const val EXTRA_DATE = "date"
private const val EXTRA_TIME = "time"
private const val EXTRA_AT = "at"
private const val EXTRA_CAN_MARK_DONE = "canMarkDone"
