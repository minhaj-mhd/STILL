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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.launcher.R
import app.still.launcher.data.AppScreenTime
import app.still.launcher.precision.InterventionKind
import app.still.launcher.ui.apps.AppActionsSheet
import app.still.launcher.ui.apps.AppsPage
import app.still.launcher.ui.apps.BlockAppSheet
import app.still.launcher.ui.apps.BlockedScreen
import app.still.launcher.ui.apps.LaunchDelayScreen
import app.still.launcher.ui.apps.TimeIntentSheet
import app.still.launcher.ui.apps.TimeUpScreen
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/** The three swipeable pages, left to right. STILL always opens on [Home]. */
private enum class Page { Productivity, Home, Apps }

/** Full-screen views over the pages, opened by swiping up on home. Back goes up this list. */
private enum class Overlay { INSIGHTS, SETTINGS, HIDDEN_APPS, BLOCKED_APPS, SCHEDULES, PRECISION }

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
    var launchDelayKey by rememberSaveable { mutableStateOf<String?>(null) }
    var timeAskKey by rememberSaveable { mutableStateOf<String?>(null) }
    var timeUpKey by rememberSaveable { mutableStateOf<String?>(null) }
    var timeUpMinutes by rememberSaveable { mutableIntStateOf(0) }
    var showLockHelp by rememberSaveable { mutableStateOf(false) }
    // Look apps up by key so an open menu reflects changes (e.g. favorite toggled) immediately.
    val menuApp = state.apps.firstOrNull { it.key == menuAppKey }
    val renameApp = state.apps.firstOrNull { it.key == renameAppKey }
    val blockApp = state.apps.firstOrNull { it.key == blockAppKey }
    val blockedScreenApp = state.apps.firstOrNull { it.key == blockedScreenKey }
    val launchDelayApp = state.apps.firstOrNull { it.key == launchDelayKey }
    val timeAskApp = state.apps.firstOrNull { it.key == timeAskKey }
    val timeUpApp = state.apps.firstOrNull { it.key == timeUpKey }

    var isDefaultLauncher by remember { mutableStateOf(context.isDefaultLauncher()) }
    var hasUsageAccess by remember { mutableStateOf(viewModel.hasUsageAccess()) }
    LifecycleResumeEffect(Unit) {
        isDefaultLauncher = context.isDefaultLauncher()
        hasUsageAccess = viewModel.hasUsageAccess()
        onPauseOrDispose { }
    }
    val precisionRunning by viewModel.precisionRunning.collectAsStateWithLifecycle()
    // Android can turn Precision mode off, e.g. after an update. The service takes a moment to
    // connect when STILL starts, so only say so once it has had time to.
    val precisionLost by produceState(false, state.precisionWanted, precisionRunning) {
        value = false
        if (state.precisionWanted && !precisionRunning) {
            delay(PRECISION_CONNECT_MILLIS)
            value = true
        }
    }
    // Screen time for whichever app the menu or block sheet is about.
    val screenTimeApp = blockApp ?: menuApp ?: timeAskApp ?: launchDelayApp ?: timeUpApp
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

    // Opening an app from STILL goes: blocked? → launch delay → "how long?" → open.
    fun openApp(app: LauncherApp) {
        if (viewModel.launch(app)) {
            query = ""
            // Coming back to STILL should land on home, not on the list or page you left from.
            overlay = null
            goHome(animate = false)
        } else {
            Toast.makeText(context, resources.getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    fun afterLaunchDelay(app: LauncherApp) {
        if (app.hasTimeReminder) timeAskKey = app.key else openApp(app)
    }

    fun launchApp(app: LauncherApp) {
        when {
            app.isBlocked -> blockedScreenKey = app.key
            app.hasLaunchDelay -> launchDelayKey = app.key
            else -> afterLaunchDelay(app)
        }
    }

    /** Closes everything over the pages: menus, sheets, editors, Insights and Settings. */
    fun closeAll() {
        query = ""
        menuAppKey = null
        renameAppKey = null
        blockAppKey = null
        blockedScreenKey = null
        launchDelayKey = null
        timeAskKey = null
        timeUpKey = null
        showLockHelp = false
        editor = null
        overlay = null
    }

    // Pressing Home while STILL is already showing returns to the home page.
    LaunchedEffect(homePresses) {
        homePresses.collect {
            closeAll()
            goHome(animate = true)
        }
    }

    // Precision mode caught an app coming to the front and brought STILL over it: step in the
    // way STILL would have if the app had been opened from here.
    LaunchedEffect(viewModel) {
        viewModel.interventions.collect { request ->
            val apps = viewModel.uiState.first { it.apps.isNotEmpty() }.apps
            // Blocks and app settings cover a package in every profile; prefer the personal app.
            val app = apps.firstOrNull { it.component.packageName == request.packageName && !it.isWorkProfile }
                ?: apps.firstOrNull { it.component.packageName == request.packageName }
                ?: return@collect
            closeAll()
            goHome(animate = false)
            when (request.kind) {
                InterventionKind.BLOCKED -> {
                    // A block that just started (a schedule, or time's up) can take a moment to
                    // reach the app list; the blocked screen closes itself if it isn't there.
                    viewModel.setNow(Instant.now())
                    withTimeoutOrNull(BLOCK_SHOWS_WITHIN_MILLIS) {
                        viewModel.uiState.first { current -> current.apps.any { it.key == app.key && it.isBlocked } }
                    }
                    blockedScreenKey = app.key
                }
                InterventionKind.ASK -> launchApp(app)
                InterventionKind.TIME_UP -> {
                    timeUpMinutes = request.plannedMinutes
                    timeUpKey = app.key
                }
            }
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
                    onDoubleTap = if (state.doubleTapToLock) {
                        { if (!viewModel.lockScreen()) showLockHelp = true }
                    } else {
                        null
                    },
                    precisionLost = precisionLost,
                    onTurnOnPrecision = { overlay = Overlay.PRECISION },
                    onDismissPrecisionLost = viewModel::forgetPrecision,
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
            Overlay.SETTINGS, Overlay.HIDDEN_APPS, Overlay.BLOCKED_APPS, Overlay.SCHEDULES, Overlay.PRECISION -> SettingsScreen(
                page = when (current) {
                    Overlay.HIDDEN_APPS -> SettingsPage.HIDDEN_APPS
                    Overlay.BLOCKED_APPS -> SettingsPage.BLOCKED_APPS
                    Overlay.SCHEDULES -> SettingsPage.SCHEDULES
                    Overlay.PRECISION -> SettingsPage.PRECISION
                    else -> SettingsPage.MAIN
                },
                onNavigate = { page ->
                    overlay = when (page) {
                        SettingsPage.MAIN -> Overlay.SETTINGS
                        SettingsPage.HIDDEN_APPS -> Overlay.HIDDEN_APPS
                        SettingsPage.BLOCKED_APPS -> Overlay.BLOCKED_APPS
                        SettingsPage.SCHEDULES -> Overlay.SCHEDULES
                        SettingsPage.PRECISION -> Overlay.PRECISION
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
                launchDelaySeconds = state.launchDelaySeconds,
                onSetLaunchDelaySeconds = viewModel::setLaunchDelaySeconds,
                timeUpBlockMinutes = state.timeUpBlockMinutes,
                onSetTimeUpBlockMinutes = viewModel::setTimeUpBlockMinutes,
                precisionOn = precisionRunning,
                onTurnOnPrecision = { context.startActivity(viewModel.precisionSettingsIntent()) },
                onTurnOffPrecision = viewModel::turnOffPrecision,
                onOpenAppInfo = { context.startActivity(viewModel.appInfoIntent()) },
                doubleTapToLock = state.doubleTapToLock,
                onSetDoubleTapToLock = viewModel::setDoubleTapToLock,
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

    launchDelayApp?.let { app ->
        LaunchDelayScreen(
            appLabel = app.label,
            seconds = state.launchDelaySeconds,
            screenTime = screenTime,
            onOpen = {
                launchDelayKey = null
                afterLaunchDelay(app)
            },
            onCancel = { launchDelayKey = null },
        )
    }

    // Precision mode's "Time's up" over the app; "More time" asks how long again.
    timeUpApp?.let { app ->
        TimeUpScreen(
            appLabel = app.label,
            plannedMinutes = timeUpMinutes,
            screenTime = screenTime,
            onLeave = { timeUpKey = null },
            onMoreTime = {
                timeUpKey = null
                timeAskKey = app.key
            },
        )
    }

    timeAskApp?.let { app ->
        TimeIntentSheet(
            appLabel = app.label,
            screenTime = screenTime,
            onStart = { minutes ->
                timeAskKey = null
                viewModel.startTimeSession(app, minutes)
                openApp(app)
            },
            onDismiss = { timeAskKey = null },
        )
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
            onSetLaunchDelay = { on ->
                viewModel.setLaunchDelay(app, on)
                menuAppKey = null
            },
            onSetTimeReminder = { on ->
                viewModel.setTimeReminder(app, on)
                menuAppKey = null
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

    if (showLockHelp) {
        AlertDialog(
            onDismissRequest = { showLockHelp = false },
            title = { Text(stringResource(R.string.lock_needs_precision_title)) },
            text = { Text(stringResource(R.string.lock_needs_precision_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLockHelp = false
                        overlay = Overlay.PRECISION
                    },
                ) { Text(stringResource(R.string.lock_set_up)) }
            },
            dismissButton = {
                TextButton(onClick = { showLockHelp = false }) { Text(stringResource(R.string.lock_not_now)) }
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

/** How long the Precision mode service gets to connect before home says it's off. */
private const val PRECISION_CONNECT_MILLIS = 3_000L

/** How long Precision mode's blocked screen waits for a block that just started to show up. */
private const val BLOCK_SHOWS_WITHIN_MILLIS = 2_000L
