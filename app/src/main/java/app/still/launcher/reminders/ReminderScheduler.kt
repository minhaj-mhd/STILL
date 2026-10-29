package app.still.launcher.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.productivity.ACTIVE_REMINDER_LOOKBACK_DAYS
import app.still.launcher.productivity.REMINDER_LOOKAHEAD_DAYS
import app.still.launcher.productivity.Reminder
import app.still.launcher.productivity.nextReminderAt
import app.still.launcher.productivity.remindersBetween
import app.still.launcher.productivity.withPlanDates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps one alarm set for the next reminder. When it goes off, every reminder due since the
 * last check is shown and the alarm moves on to the next one. Changes to tasks, habits or
 * their records move it too, so ticking something off early cancels its reminder.
 */
class ReminderScheduler(
    context: Context,
    private val repository: ProductivityRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val prefs = appContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val notifier = ReminderNotifier(appContext)

    /** Whether reminders can show at all (notification permission and settings). */
    fun canNotify(): Boolean = notifier.canNotify()

    /** Starts following data changes for as long as the app process lives. */
    fun start() {
        notifier.createChannel()
        scope.launch {
            repository.changes.collectLatest {
                delay(SETTLE_MILLIS) // let a burst of edits settle before recalculating
                rescheduleNext()
            }
        }
    }

    /** Whether alarms fire at the exact minute; otherwise Android may delay them a little. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun rescheduleNext() {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val next = nextReminderAt(
            now,
            repository.getActiveTasks().withPlanDates(repository.getPlans(), today),
            repository.getActiveHabits(),
            repository.getRecords(today, today.plusDays(REMINDER_LOOKAHEAD_DAYS)),
        )
        val alarm = checkIntent()
        if (next == null) alarmManager.cancel(alarm) else setAlarm(next, alarm)
    }

    /** The alarm went off: show what's due since the last check (at most an hour back), then re-arm. */
    suspend fun onCheckAlarm() {
        val now = LocalDateTime.now()
        val earliest = now.minusMinutes(CATCH_UP_MINUTES)
        val from = lastCheck()?.takeIf { it in earliest..now } ?: earliest
        remindersBetween(
            from,
            now,
            repository.getActiveTasks().withPlanDates(repository.getPlans(), now.toLocalDate()),
            repository.getActiveHabits(),
            repository.getRecords(from.toLocalDate(), now.toLocalDate()),
        ).forEach(notifier::show)
        prefs.edit { putString(KEY_LAST_CHECK, now.toString()) }
        repository.forgetDismissedRemindersBefore(now.toLocalDate().minusDays(ACTIVE_REMINDER_LOOKBACK_DAYS + 1))
        rescheduleNext()
    }

    /** A reminder card was swiped off the home screen: keep it off, and clear its notification too. */
    suspend fun dismiss(reminder: Reminder) {
        repository.dismissReminder(reminder.key, reminder.occurrence.date)
        notifier.cancel(reminder)
    }

    /** "Done" on a notification: tick the task, or check the habit for that occurrence. */
    suspend fun markDone(reminder: Reminder) {
        when (reminder.itemType) {
            ItemType.TASK -> repository.getTask(reminder.itemId)?.let { task ->
                repository.setTaskDone(task, reminder.occurrence, done = true)
            }
            ItemType.HABIT -> repository.getHabit(reminder.itemId)?.let { habit ->
                repository.setHabitProgress(habit, reminder.occurrence, habit.target)
            }
        }
        notifier.cancel(reminder)
    }

    /** "Snooze" on a notification: hide it and show it again in [SNOOZE_MINUTES]. */
    fun snooze(reminder: Reminder) {
        notifier.cancel(reminder)
        val intent = reminderIntent(appContext, ReminderReceiver.ACTION_SNOOZE_ENDED, reminder)
        val pending = PendingIntent.getBroadcast(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        setAlarm(LocalDateTime.now().plusMinutes(SNOOZE_MINUTES), pending)
    }

    /** Shows a snoozed reminder again, unless it was done or skipped in the meantime. */
    suspend fun onSnoozeEnded(reminder: Reminder) {
        val date = reminder.occurrence.date
        val stillDue = remindersBetween(
            reminder.at.minusMinutes(1),
            reminder.at,
            repository.getActiveTasks().withPlanDates(repository.getPlans(), LocalDate.now()),
            repository.getActiveHabits(),
            repository.getRecords(date, date),
        ).any { it.key == reminder.key }
        if (stillDue) notifier.show(reminder)
    }

    private fun setAlarm(at: LocalDateTime, operation: PendingIntent) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    }

    private fun checkIntent(): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_CHECK)
        return PendingIntent.getBroadcast(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun lastCheck(): LocalDateTime? =
        prefs.getString(KEY_LAST_CHECK, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    private companion object {
        const val KEY_LAST_CHECK = "lastCheck"
        const val SETTLE_MILLIS = 300L
        const val SNOOZE_MINUTES = 10L

        /** A late alarm (or one after a reboot) still shows reminders from up to this long ago. */
        const val CATCH_UP_MINUTES = 60L
    }
}
