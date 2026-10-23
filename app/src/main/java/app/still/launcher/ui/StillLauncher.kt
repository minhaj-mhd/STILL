package app.still.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.launcher.R
import app.still.launcher.ui.apps.AppActionsSheet
import app.still.launcher.ui.apps.AppsPage
import app.still.launcher.ui.apps.RenameAppDialog
import app.still.launcher.ui.home.HomePage
import app.still.launcher.ui.productivity.ProductivityPage
import app.still.launcher.util.defaultLauncherRequestIntent
import app.still.launcher.util.homeSettingsIntent
import app.still.launcher.util.isDefaultLauncher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The three swipeable pages, left to right. STILL always opens on [Home]. */
private enum class Page { Productivity, Home, Apps }

@Composable
fun StillLauncher(
    viewModel: LauncherViewModel,
    homePresses: Flow<Unit>,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = Page.Home.ordinal) { Page.entries.size }
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var menuAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    var renameAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Look apps up by key so an open menu reflects changes (e.g. favorite toggled) immediately.
    val menuApp = state.apps.firstOrNull { it.key == menuAppKey }
    val renameApp = state.apps.firstOrNull { it.key == renameAppKey }

    var isDefaultLauncher by remember { mutableStateOf(context.isDefaultLauncher()) }
    LifecycleResumeEffect(Unit) {
        isDefaultLauncher = context.isDefaultLauncher()
        onPauseOrDispose { }
    }
    val defaultLauncherRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        isDefaultLauncher = context.isDefaultLauncher()
    }

    fun goHome(animate: Boolean) {
        scope.launch {
            if (animate) pagerState.animateScrollToPage(Page.Home.ordinal)
            else pagerState.scrollToPage(Page.Home.ordinal)
        }
    }

    fun launchApp(app: LauncherApp) {
        if (viewModel.launch(app)) {
            query = ""
            // Coming back to STILL should land on home, not on the list you left from.
            goHome(animate = false)
        } else {
            Toast.makeText(context, resources.getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    // Pressing Home while STILL is already showing returns to the home page.
    LaunchedEffect(homePresses) {
        homePresses.collect {
            query = ""
            menuAppKey = null
            renameAppKey = null
            goHome(animate = true)
        }
    }

    // A launcher has nowhere to go back to: Back only returns to the home page.
    BackHandler {
        if (pagerState.currentPage != Page.Home.ordinal) goHome(animate = true)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (Page.entries[page]) {
                Page.Productivity -> ProductivityPage()
                Page.Home -> HomePage(
                    favorites = state.favorites,
                    isDefaultLauncher = isDefaultLauncher,
                    onSetDefaultLauncher = {
                        try {
                            defaultLauncherRequest.launch(context.defaultLauncherRequestIntent())
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(homeSettingsIntent())
                        }
                    },
                    onClockClick = {
                        try {
                            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                        } catch (e: ActivityNotFoundException) {
                            // No clock app installed; nothing to open.
                        }
                    },
                    onLaunch = ::launchApp,
                    onLongPress = { menuAppKey = it.key },
                )
                Page.Apps -> AppsPage(
                    apps = state.apps,
                    query = query,
                    onQueryChange = { query = it },
                    isCurrentPage = pagerState.currentPage == Page.Apps.ordinal,
                    onLaunch = ::launchApp,
                    onLongPress = { menuAppKey = it.key },
                )
            }
        }
    }

    menuApp?.let { app ->
        AppActionsSheet(
            app = app,
            onDismiss = { menuAppKey = null },
            onSetFavorite = { favorite ->
                viewModel.setFavorite(app, favorite)
                menuAppKey = null
            },
            onRename = {
                menuAppKey = null
                renameAppKey = app.key
            },
            onSetHidden = { hidden ->
                viewModel.setHidden(app, hidden)
                menuAppKey = null
            },
            onAppInfo = {
                viewModel.openAppInfo(app)
                menuAppKey = null
            },
            onUninstall = {
                viewModel.uninstall(app)
                menuAppKey = null
            },
        )
    }

    renameApp?.let { app ->
        RenameAppDialog(
            app = app,
            onDismiss = { renameAppKey = null },
            onSave = { label ->
                viewModel.rename(app, label)
                renameAppKey = null
            },
        )
    }
}
