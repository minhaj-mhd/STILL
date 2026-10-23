package app.still.launcher

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.still.launcher.data.AppRepository
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.StillDatabase

private val Context.launcherDataStore by preferencesDataStore(name = "launcher")

/** Manual dependency container; one instance lives in [StillApplication]. */
class AppContainer(context: Context) {
    private val database = StillDatabase.create(context)

    val preferences = LauncherPreferences(context.launcherDataStore)
    val appRepository = AppRepository(context)
    val productivityRepository = ProductivityRepository(database.productivityDao())
}
