package app.still.launcher

import android.app.Application
import kotlinx.coroutines.launch

class StillApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.reminderScheduler.start()
        container.appTimeReminders.createChannel()
        container.focusTimer.createChannel()
        container.notificationFilter.createChannel()
        container.dailyLimit.createChannel()
        container.notificationFilter.rebindIfAllowed()
        // Finished days are saved whenever STILL starts too, not only when home shows.
        container.applicationScope.launch { container.usageHistory.catchUp() }
    }
}
