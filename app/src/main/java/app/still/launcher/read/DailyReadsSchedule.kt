package app.still.launcher.read

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.still.launcher.StillApplication
import app.still.launcher.data.DriveResult
import app.still.launcher.drive.DriveProblem
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * When STILL looks for daily reads: a few minutes after each series is due (an alarm, which then
 * hands over to WorkManager to wait for a connection), and every few hours besides, in case a chat
 * saves at an unusual time.
 */
class DailyReadsSchedule(context: Context) {

    private val appContext = context.applicationContext
    private val work get() = WorkManager.getInstance(appContext)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    /** Keeps the regular look going; does nothing if it already is. */
    fun keepChecking() {
        val request = PeriodicWorkRequestBuilder<DailyReadsWorker>(REGULAR_HOURS, TimeUnit.HOURS)
            .setConstraints(online)
            .build()
        work.enqueueUniquePeriodicWork(REGULAR_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Looks at [at], roughly; Android may move it a little to save battery. */
    fun checkAt(at: Instant) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), alarmIntent())
    }

    fun cancelNext() {
        alarmManager.cancel(alarmIntent())
    }

    /** Looks as soon as there's a connection. */
    fun checkSoon() {
        val request = OneTimeWorkRequestBuilder<DailyReadsWorker>().setConstraints(online).build()
        work.enqueueUniqueWork(SOON_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun stop() {
        cancelNext()
        work.cancelUniqueWork(REGULAR_WORK)
        work.cancelUniqueWork(SOON_WORK)
    }

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(appContext, DailyReadsAlarm::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REGULAR_WORK = "daily-reads-regular"
        const val SOON_WORK = "daily-reads-soon"
        const val REGULAR_HOURS = 6L
    }
}

/** A series is due: look once there's a connection. */
class DailyReadsAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as StillApplication).container.dailyReadsSchedule.checkSoon()
    }
}

/** Looks in every series' folder for new reads. */
class DailyReadsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as StillApplication).container
        return when (val result = container.dailyReads.sync()) {
            is DriveResult.Failed -> if (result.problem == DriveProblem.OFFLINE && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
