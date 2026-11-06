package app.still.launcher

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.still.launcher.blocking.TabOwners
import app.still.launcher.data.AppIcons
import app.still.launcher.data.AppRepository
import app.still.launcher.data.BlockingRepository
import app.still.launcher.data.GateRepository
import app.still.launcher.data.HistoryRepository
import app.still.launcher.data.ReadRepository
import app.still.launcher.read.Publications
import app.still.launcher.focus.FocusTimer
import app.still.launcher.focusmode.FocusModeMonitor
import app.still.launcher.gates.TaskGateMonitor
import app.still.launcher.history.UsageHistory
import app.still.launcher.keynote.UnlockMonitor
import app.still.launcher.limit.AppLimitMonitor
import app.still.launcher.limit.DailyLimitMonitor
import app.still.launcher.notifications.NotificationFilter
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.util.EssentialApps
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.NotesRepository
import app.still.launcher.data.PracticeRepository
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.StillDatabase
import app.still.launcher.precision.PrecisionMode
import app.still.launcher.reminders.AppTimeReminders
import app.still.launcher.reminders.ReminderScheduler
import app.still.launcher.shortvideo.ShortVideoBlocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.launcherDataStore by preferencesDataStore(name = "launcher")

/** Manual dependency container; one instance lives in [StillApplication]. */
class AppContainer(context: Context) {
    private val database = StillDatabase.create(context)

    /** For work that outlives a screen, e.g. broadcast receivers and reminder scheduling. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = LauncherPreferences(context.launcherDataStore)
    val appRepository = AppRepository(context)
    val appIcons = AppIcons(context)
    val productivityRepository = ProductivityRepository(database.productivityDao())
    val notesRepository = NotesRepository(database.noteDao())
    val blockingRepository = BlockingRepository(database.appBlockDao(), database.blockingScheduleDao())
    val essentialApps = EssentialApps(context)

    /** Which app a browser's tab counts as. STILL and Android's own screens don't open tabs. */
    val tabOwners = TabOwners { it != context.packageName && !essentialApps.isSystemScreen(it) }
    val screenTimeRepository = ScreenTimeRepository(context, tabOwners)
    val gateRepository = GateRepository(database.taskGateDao())
    val taskGates = TaskGateMonitor(productivityRepository, gateRepository, applicationScope)
    val precisionMode = PrecisionMode(context)
    val dailyLimit = DailyLimitMonitor(context, preferences, screenTimeRepository, essentialApps, applicationScope)
    val appTimeReminders = AppTimeReminders(context, blockingRepository, screenTimeRepository, preferences, precisionMode)
    val reminderScheduler = ReminderScheduler(context, productivityRepository, applicationScope)
    val focusTimer = FocusTimer(context, preferences, productivityRepository, applicationScope)
    val practiceRepository = PracticeRepository(database.practiceDao())
    val unlockMonitor = UnlockMonitor(screenTimeRepository)
    val historyRepository = HistoryRepository(database.historyDao())
    val publications = Publications(context)
    val readRepository = ReadRepository(context, database.readDao(), publications, preferences, productivityRepository)
    val usageHistory = UsageHistory(context, screenTimeRepository, preferences, historyRepository, essentialApps)
    val focusMode = FocusModeMonitor(context, preferences, essentialApps, historyRepository, applicationScope)
    val appLimits = AppLimitMonitor(context, preferences, dailyLimit, appTimeReminders, precisionMode, practiceRepository, focusTimer, applicationScope)
    val notificationFilter = NotificationFilter(context, database.heldNotificationDao(), preferences, essentialApps, applicationScope)
    val shortVideos = ShortVideoBlocker(context, preferences, applicationScope)
}
