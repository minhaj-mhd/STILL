package app.still.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.launcher.ui.read.daily.DailyReadsScreens
import app.still.launcher.ui.read.daily.DailyReadsViewModel
import app.still.launcher.ui.theme.Appearance
import app.still.launcher.ui.theme.appearance
import app.still.launcher.ui.theme.StillTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Daily reads from ChatGPT's scheduled chats: connecting Google Drive, the series and their reads.
 * Opened from the Read tab.
 */
class DailyReadsActivity : ComponentActivity() {

    private val viewModel: DailyReadsViewModel by viewModels { DailyReadsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val preferences = (application as StillApplication).container.preferences
        val startSeries = intent.getLongExtra(EXTRA_SERIES_ID, -1L).takeIf { it > 0 }
        setContent {
            val appearanceFlow = remember {
                preferences.settings.map { it.appearance() }.distinctUntilChanged()
            }
            val appearance by appearanceFlow.collectAsStateWithLifecycle(Appearance())
            LaunchedEffect(appearance.isLight) {
                val style = if (appearance.isLight) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            StillTheme(appearance) {
                DailyReadsScreens(viewModel, startSeries, onClose = ::finish)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        OwnScreens.markLeft()
    }

    companion object {
        private const val EXTRA_SERIES_ID = "app.still.launcher.SERIES_ID"

        /** Daily reads, opened on [seriesId]'s page when given. */
        fun intent(context: Context, seriesId: Long? = null): Intent =
            Intent(context, DailyReadsActivity::class.java).apply { if (seriesId != null) putExtra(EXTRA_SERIES_ID, seriesId) }
    }
}
