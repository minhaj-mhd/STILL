package app.still.launcher

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.still.launcher.data.AppRepository
import app.still.launcher.data.BlockingRepository
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.util.EssentialApps
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.NotesRepository
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.StillDatabase
import app.still.launcher.reminders.ReminderScheduler
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
    val productivityRepository = ProductivityRepository(database.productivityDao())
    val notesRepository = NotesRepository(database.noteDao())
    val blockingRepository = BlockingRepository(database.appBlockDao())
    val screenTimeRepository = ScreenTimeRepository(context)
    val essentialApps = EssentialApps(context)
    val reminderScheduler = ReminderScheduler(context, productivityRepository, applicationScope)
}
