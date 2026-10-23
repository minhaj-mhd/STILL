package app.still.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import app.still.launcher.data.AppScreenTime
import app.still.launcher.ui.apps.AppActionsSheet
import app.still.launcher.ui.apps.AppsPage
import app.still.launcher.ui.apps.BlockAppSheet
import app.still.launcher.ui.apps.BlockedScreen
import app.still.launcher.ui.apps.RenameAppDialog
import app.still.launcher.ui.components.rememberCurrentMinute
import app.still.launcher.ui.editor.EditorHost
import app.still.launcher.ui.editor.EditorTarget
import app.still.launcher.ui.editor.EditorTargetSaver
import app.still.launcher.ui.home.HomePage
import app.still.launcher.ui.insights.InsightsPage
import app.still.launcher.ui.notes.NotesPage
import app.still.launcher.ui.settings.SettingsPage
import app.still.launcher.ui.settings.SettingsScreen
import app.still.launcher.ui.productivity.LeftPageTabs
import app.still.launcher.ui.productivity.LeftTab
import app.still.launcher.ui.productivity.TodayPage
import app.still.launcher.ui.productivity.TodayViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.util.defaultLauncherRequestIntent
import app.still.launcher.util.homeSettingsIntent
import app.still.launcher.util.isDefaultLauncher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The three swipeable pages, left to right. STILL always opens on [Home]. */
private enum class Page { Productivity, Home, Apps }

/** Full-screen views over the pages, opened by swiping up on home. Back goes up this list. */
private enum class Overlay { INSIGHTS, SETTINGS, HIDDEN_APPS, BLOCKED_APPS, SCHEDULES }

@Composable
fun StillLauncher(
    viewModel: LauncherViewModel,
    homePresses: Flow<Unit>,
    todayRequests: Flow<Unit>,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Shared with the Today page, so home's due count always matches what the Today page lists.
    val todayViewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
    val agenda by todayViewModel.agenda.collectAsStateWithLifecycle()
    val activeReminders by todayViewModel.activeReminders.collectAsStateWithLifecycle()
    // One clock for everything time-based: the Today page's date and home's reminder cards.
    val now by rememberCurrentMinute()
    LaunchedEffect(now) {
        todayViewModel.setNow(now.toLocalDateTime())
        viewModel.setNow(now.toInstant())
    }
    val pagerState = rememberPagerState(initialPage = Page.Home.ordinal) { Page.entries.size }
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var menuAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    var renameAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable(stateSaver = EditorTargetSaver) { mutableStateOf<EditorTarget?>(null) }
    var leftTab by rememberSaveable { mutableStateOf(LeftTab.TODAY) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var blockAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    var blockedScreenKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Look apps up by key so an open menu reflects changes (e.g. favorite toggled) immediately.
    val menuApp = state.apps.firstOrNull { it.key == menuAppKey }
    val renameApp = state.apps.firstOrNull { it.key == renameAppKey }
    val blockApp = state.apps.firstOrNull { it.key == blockAppKey }
    val blockedScreenApp = state.apps.firstOrNull { it.key == blockedScreenKey }

    var isDefaultLauncher by remember { mutableStateOf(context.isDefaultLauncher()) }
    var hasUsageAccess by remember { mutableStateOf(viewModel.hasUsageAccess()) }
    LifecycleResumeEffect(Unit) {
        isDefaultLauncher = context.isDefaultLauncher()
        hasUsageAccess = viewModel.hasUsageAccess()
        onPauseOrDispose { }
    }
    // Screen time for whichever app the menu or block sheet is about.
    val screenTimeApp = blockApp ?: menuApp
    val screenTime by produceState<AppScreenTime?>(null, screenTimeApp?.key, hasUsageAccess) {
        value = screenTimeApp?.let { viewModel.screenTime(it) }
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

    fun requestDefaultLauncher() {
        try {
            defaultLauncherRequest.launch(context.defaultLauncherRequestIntent())
        } catch (e: ActivityNotFoundException) {
            context.startActivity(homeSettingsIntent())
        }
    }

    fun launchApp(app: LauncherApp) {
        if (app.isBlocked) {
            blockedScreenKey = app.key
            return
        }
        if (viewModel.launch(app)) {
            query = ""
            // Coming back to STILL should land on home, not on the list or page you left from.
            overlay = null
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
            blockAppKey = null
            blockedScreenKey = null
            editor = null
            overlay = null
            goHome(animate = true)
        }
    }

    // A reminder notification was tapped: show the Today page.
    LaunchedEffect(todayRequests) {
        todayRequests.collect {
            editor = null
            overlay = null
            leftTab = LeftTab.TODAY
            pagerState.animateScrollToPage(Page.Productivity.ordinal)
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
                Page.Productivity -> {
                    val tabs = @Composable { LeftPageTabs(leftTab) { leftTab = it } }
                    when (leftTab) {
                        LeftTab.TODAY -> TodayPage(tabs = tabs, onOpenEditor = { editor = it }, viewModel = todayViewModel)
                        LeftTab.NOTES -> NotesPage(tabs = tabs, onOpenEditor = { editor = it })
                    }
                }
                Page.Home -> HomePage(
                    favorites = state.favorites,
                    isDefaultLauncher = isDefaultLauncher,
                    onSetDefaultLauncher = ::requestDefaultLauncher,
                    onClockClick = {
                        try {
                            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                        } catch (e: ActivityNotFoundException) {
                            // No clock app installed; nothing to open.
                        }
                    },
                    dueTasks = agenda?.dueTaskCount ?: 0,
                    overdueTasks = agenda?.overdueTaskCount ?: 0,
                    onDueTasksClick = {
                        leftTab = LeftTab.TODAY
                        scope.launch { pagerState.animateScrollToPage(Page.Productivity.ordinal) }
                    },
                    reminders = activeReminders,
                    today = now.toLocalDate(),
                    onReminderDone = todayViewModel::completeReminder,
                    onReminderDismiss = todayViewModel::dismissReminder,
                    onLaunch = ::launchApp,
                    onLongPress = { menuAppKey = it.key },
                    onSwipeUp = { overlay = Overlay.INSIGHTS },
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

    // Insights slides up from home; Settings and its lists open on top of it.
    AnimatedContent(
        targetState = overlay,
        transitionSpec = {
            when {
                initialState == null -> slideInVertically { it } togetherWith fadeOut()
                targetState == null -> fadeIn() togetherWith slideOutVertically { it }
                else -> fadeIn() togetherWith fadeOut()
            }
        },
        label = "overlay",
        modifier = Modifier.fillMaxSize(),
    ) { current ->
        when (current) {
            null -> Unit
            Overlay.INSIGHTS -> InsightsPage(
                apps = state.apps,
                agenda = agenda,
                onOpenSettings = { overlay = Overlay.SETTINGS },
                onAllowUsageAccess = { context.startActivity(viewModel.usageAccessIntent()) },
                onClose = { overlay = null },
            )
            Overlay.SETTINGS, Overlay.HIDDEN_APPS, Overlay.BLOCKED_APPS, Overlay.SCHEDULES -> SettingsScreen(
                page = when (current) {
                    Overlay.HIDDEN_APPS -> SettingsPage.HIDDEN_APPS
                    Overlay.BLOCKED_APPS -> SettingsPage.BLOCKED_APPS
                    Overlay.SCHEDULES -> SettingsPage.SCHEDULES
                    else -> SettingsPage.MAIN
                },
                onNavigate = { page ->
                    overlay = when (page) {
                        SettingsPage.MAIN -> Overlay.SETTINGS
                        SettingsPage.HIDDEN_APPS -> Overlay.HIDDEN_APPS
                        SettingsPage.BLOCKED_APPS -> Overlay.BLOCKED_APPS
                        SettingsPage.SCHEDULES -> Overlay.SCHEDULES
                    }
                },
                onClose = { overlay = Overlay.INSIGHTS },
                apps = state.apps,
                isDefaultLauncher = isDefaultLauncher,
                onSetDefaultLauncher = ::requestDefaultLauncher,
                hasUsageAccess = hasUsageAccess,
                onAllowUsageAccess = { context.startActivity(viewModel.usageAccessIntent()) },
                canNotify = todayViewModel::canNotify,
                canScheduleExactAlarms = todayViewModel::canScheduleExactAlarms,
                onLaunch = ::launchApp,
                onLongPressApp = { menuAppKey = it.key },
                onOpenScheduleEditor = { editor = it },
            )
        }
    }

    // Editors cover everything else, including Settings when a schedule is opened from there.
    // Their own Back handler takes priority over the pages'.
    editor?.let { target -> EditorHost(target, onClose = { editor = null }) }

    // Opening a blocked app lands here instead; it closes itself when the block runs out.
    blockedScreenApp?.let { app ->
        val until = app.blockedUntil
        if (until == null) {
            LaunchedEffect(app.key) { blockedScreenKey = null }
        } else {
            BlockedScreen(app.label, until, app.blockedBySchedule, onClose = { blockedScreenKey = null })
        }
    }

    menuApp?.let { app ->
        AppActionsSheet(
            app = app,
            screenTime = screenTime,
            hasUsageAccess = hasUsageAccess,
            onDismiss = { menuAppKey = null },
            onAllowUsageAccess = { context.startActivity(viewModel.usageAccessIntent()) },
            onBlock = {
                menuAppKey = null
                blockAppKey = app.key
            },
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

    blockApp?.let { app ->
        BlockAppSheet(
            app = app,
            screenTime = screenTime,
            onDismiss = { blockAppKey = null },
            onBlock = { duration ->
                viewModel.block(app, duration)
                blockAppKey = null
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
