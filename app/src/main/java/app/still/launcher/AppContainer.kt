package app.still.launcher

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.still.launcher.data.AppRepository
import app.still.launcher.data.LauncherPreferences

private val Context.launcherDataStore by preferencesDataStore(name = "launcher")

/** Manual dependency container; one instance lives in [StillApplication]. */
class AppContainer(context: Context) {
    val preferences = LauncherPreferences(context.launcherDataStore)
    val appRepository = AppRepository(context)
}
