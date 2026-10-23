package app.still.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.still.launcher.ui.LauncherViewModel
import app.still.launcher.ui.StillLauncher
import app.still.launcher.ui.theme.StillTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels { LauncherViewModel.Factory }
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // A channel keeps the request until the UI collects it, even when it arrives before the first frame.
    private val todayRequests = Channel<Unit>(Channel.CONFLATED)
    private val heldNotificationRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleOpenRequests(intent)
        setContent {
            StillTheme {
                StillLauncher(
                    viewModel = viewModel,
                    homePresses = homePresses,
                    todayRequests = todayRequests.receiveAsFlow(),
                    heldNotificationRequests = heldNotificationRequests.receiveAsFlow(),
                )
            }
        }
    }

    // singleTask: pressing Home while STILL is the default launcher arrives here, not in onCreate.
    // A time reminder's "Leave" button lands here too, and should look the same: back on home.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra(EXTRA_GO_HOME, false)) {
            homePresses.tryEmit(Unit)
        }
        handleOpenRequests(intent)
    }

    /**
     * Tapping a reminder notification opens STILL on the Today page; tapping the held-back count
     * opens the held-back notifications.
     */
    private fun handleOpenRequests(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true) todayRequests.trySend(Unit)
        if (intent?.getBooleanExtra(EXTRA_OPEN_HELD_NOTIFICATIONS, false) == true) heldNotificationRequests.trySend(Unit)
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "app.still.launcher.OPEN_TODAY"
        const val EXTRA_GO_HOME = "app.still.launcher.GO_HOME"
        const val EXTRA_OPEN_HELD_NOTIFICATIONS = "app.still.launcher.OPEN_HELD_NOTIFICATIONS"
    }
}
