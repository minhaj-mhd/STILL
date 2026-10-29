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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.still.launcher.R
import app.still.launcher.data.AppScreenTime
import app.still.launcher.data.practice.ChoiceSource
import app.still.launcher.limit.AppClosed
import app.still.launcher.limit.Pause
import app.still.launcher.limit.dayStart
import app.still.launcher.limit.LimitSettings
import app.still.launcher.precision.InterventionKind
import app.still.launcher.productivity.progress
import app.still.launcher.ui.apps.AppActionsSheet
import app.still.launcher.ui.apps.AppsPage
import app.still.launcher.ui.apps.BlockAppSheet
import app.still.launcher.ui.apps.BlockedScreen
import app.still.launcher.ui.apps.FolderActionsSheet
import app.still.launcher.ui.apps.FolderNameDialog
import app.still.launcher.ui.apps.FolderPickerSheet
import app.still.launcher.ui.apps.LaunchDelayScreen
import app.still.launcher.ui.apps.TimeIntentSheet
import app.still.launcher.ui.apps.TimeUpScreen
import app.still.launcher.ui.apps.WhyPauseScreen
import app.still.launcher.ui.focusmode.FocusLockedScreen
import app.still.launcher.ui.keynote.HomeKeynote
import app.still.launcher.ui.keynote.KeynoteCard
import app.still.launcher.ui.keynote.KeynoteViewModel
import app.still.launcher.ui.keynote.LinkOption
import app.still.launcher.ui.keynote.MomentsSheet
import app.still.launcher.ui.keynote.QuietChoice
import app.still.launcher.ui.keynote.QuietMinuteScreen
import app.still.launcher.ui.keynote.WelcomeBackCard
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.ui.apps.RenameAppDialog
import app.still.launcher.ui.components.rememberCurrentMinute
import app.still.launcher.ui.theme.WallpaperBackground
import app.still.launcher.ui.editor.EditorHost
import app.still.launcher.ui.editor.EditorTarget
import app.still.launcher.ui.editor.EditorTargetSaver
import app.still.launcher.ui.gates.GateScreen
import app.still.launcher.ui.home.HomePage
import app.still.launcher.ui.insights.InsightsPage
import app.still.launcher.ui.limit.DailyLimitPage
import app.still.launcher.ui.limit.LimitScreen
import app.still.launcher.ui.limit.AppLimitReason
import app.still.launcher.ui.limit.AppLimitScreen
import app.still.launcher.ui.limit.AppLimitSheet
import app.still.launcher.ui.notifications.HeldNotificationsPage
import app.still.launcher.ui.notifications.NotificationFilterViewModel
import app.still.launcher.ui.notes.NotesPage
import app.still.launcher.ui.settings.CustomisePage
import app.still.launcher.ui.settings.SettingsPage
import app.still.launcher.ui.settings.SettingsScreen
import app.still.launcher.ui.productivity.LeftPageTabs
import app.still.launcher.ui.productivity.LeftTab
import app.still.launcher.ui.productivity.PlansPage
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
private enum class Overlay { INSIGHTS, DAILY_LIMIT, HOME_CUSTOMISE, SETTINGS, CUSTOMISE, HIDDEN_APPS, BLOCKED_APPS, APP_LIMITS, SCHEDULES, GATES, NOTIFICATION_FILTER, PRECISION, SHORT_VIDEOS, HELD_NOTIFICATIONS }

@Composable
fun StillLauncher(
    viewModel: LauncherViewModel,
    homePresses: Flow<Unit>,
    todayRequests: Flow<Unit>,
    heldNotificationRequests: Flow<Unit>,
    listRequests: Flow<String>,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Shared with the Today page, so home's due count always matches what the Today page lists.
    val todayViewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
    val agenda by todayViewModel.agenda.collectAsStateWithLifecycle()
    val activeReminders by todayViewModel.activeReminders.collectAsStateWithLifecycle()
    val focus by todayViewModel.focus.collectAsStateWithLifecycle()
    val keynoteViewModel: KeynoteViewModel = viewModel(factory = KeynoteViewModel.Factory)
    val keynote by keynoteViewModel.state.collectAsStateWithLifecycle()
    val filterViewModel: NotificationFilterViewModel = viewModel(factory = NotificationFilterViewModel.Factory)
    val filterSettings by filterViewModel.settings.collectAsStateWithLifecycle()
    val held by filterViewModel.held.collectAsStateWithLifecycle()
    // One clock for everything time-based: the Today page's date and home's reminder cards.
    val now by rememberCurrentMinute()
    LaunchedEffect(now) {
        todayViewModel.setNow(now.toLocalDateTime())
        viewModel.setNow(now.toInstant())
        keynoteViewModel.setNow(now.toLocalDateTime())
    }
    // Focus mode leaves home and the left page; the app list isn't there to swipe to.
    val pagerState = rememberPagerState(initialPage = Page.Home.ordinal) {
        if (state.focusModeSince != null) Page.Apps.ordinal else Page.entries.size
    }
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var menuAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    var renameAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    var folderPickerKey by rememberSaveable { mutableStateOf<String?>(null) }
    var newFolderForKey by rememberSaveable { mutableStateOf<String?>(null) }
    var folderMenuId by rememberSaveable { mutableStateOf<String?>(null) }
    var renamingFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable(stateSaver = EditorTargetSaver) { mutableStateOf<EditorTarget?>(null) }
    var leftTab by rememberSaveable { mutableStateOf(LeftTab.TODAY) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var blockAppKey by rememberSaveable { mutableStateOf<String?>(null) }
    var blockedScreenKey by rememberSaveable { mutableStateOf<String?>(null) }
    var launchDelayKey by rememberSaveable { mutableStateOf<String?>(null) }
    var timeAskKey by rememberSaveable { mutableStateOf<String?>(null) }
    var timeUpKey by rememberSaveable { mutableStateOf<String?>(null) }
    var timeUpMinutes by rememberSaveable { mutableIntStateOf(0) }
    // A gesture that needs Precision mode was used while it's off.
    var precisionHelp by rememberSaveable { mutableStateOf<PrecisionHelp?>(null) }
    var gateScreenKey by rememberSaveable { mutableStateOf<String?>(null) }
    var limitScreenKey by rememberSaveable { mutableStateOf<String?>(null) }
    var limitEmergencyKey by rememberSaveable { mutableStateOf<String?>(null) }
    var emergencyKey by rememberSaveable { mutableStateOf<String?>(null) }
    var appLimitSheetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var focusLockedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var appLimitScreenKey by rememberSaveable { mutableStateOf<String?>(null) }
    var appLimitReason by rememberSaveable { mutableStateOf<AppLimitReason?>(null) }
    var visitUpMinutes by rememberSaveable { mutableIntStateOf(0) }
    var pauseKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Worked out when the app is tapped: asking changes what's due next time.
    var pause by remember { mutableStateOf<Pause?>(null) }
    var momentsOpen by rememberSaveable { mutableStateOf(false) }
    var quietMinute by rememberSaveable { mutableStateOf(false) }
    // When STILL last went to the background, to greet you after a while away during a focus session.
    var leftAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    var welcomeBack by rememberSaveable { mutableStateOf(false) }
    // A greeting belongs to the session it was for.
    LaunchedEffect(focus == null) { if (focus == null) welcomeBack = false }
    var changingKeynote by rememberSaveable { mutableStateOf(false) }
    // Today's unfinished tasks and habits, to practise the day's quality with.
    val linkOptions = remember(agenda) {
        agenda?.let { today ->
            (today.overdue + today.today).filter { it.isPending }.map { LinkOption(ItemType.TASK, it.task.id, it.task.title) } +
                today.habits.filter { it.isPending }.map { LinkOption(ItemType.HABIT, it.habit.id, it.habit.title) }
        }.orEmpty().distinctBy { it.type to it.id }
    }
    // Look apps up by key so an open menu reflects changes (e.g. favorite toggled) immediately.
    val menuApp = state.apps.firstOrNull { it.key == menuAppKey }
    val renameApp = state.apps.firstOrNull { it.key == renameAppKey }
    val folderPickerApp = state.apps.firstOrNull { it.key == folderPickerKey }
    val newFolderApp = state.apps.firstOrNull { it.key == newFolderForKey }
    val folderMenu = state.folders.firstOrNull { it.id == folderMenuId }
    val renamingFolder = state.folders.firstOrNull { it.id == renamingFolderId }
    val blockApp = state.apps.firstOrNull { it.key == blockAppKey }
    val blockedScreenApp = state.apps.firstOrNull { it.key == blockedScreenKey }
    val launchDelayApp = state.apps.firstOrNull { it.key == launchDelayKey }
    val timeAskApp = state.apps.firstOrNull { it.key == timeAskKey }
    val timeUpApp = state.apps.firstOrNull { it.key == timeUpKey }
    val gateScreenApp = state.apps.firstOrNull { it.key == gateScreenKey }
    val limitScreenApp = state.apps.firstOrNull { it.key == limitScreenKey }
    val limitEmergencyApp = state.apps.firstOrNull { it.key == limitEmergencyKey }
    val emergencyApp = state.apps.firstOrNull { it.key == emergencyKey }
    val appLimitSheetApp = state.apps.firstOrNull { it.key == appLimitSheetKey }
    val focusLockedApp = state.apps.firstOrNull { it.key == focusLockedKey }
    val focusOn = state.focusModeSince != null
    val appLimitScreenApp = state.apps.firstOrNull { it.key == appLimitScreenKey }
    val pauseApp = state.apps.firstOrNull { it.key == pauseKey }

    var isDefaultLauncher by remember { mutableStateOf(context.isDefaultLauncher()) }
    var hasUsageAccess by remember { mutableStateOf(viewModel.hasUsageAccess()) }
    var precisionSwitchedOn by remember { mutableStateOf(viewModel.isPrecisionSwitchedOn()) }
    LifecycleResumeEffect(Unit) {
        isDefaultLauncher = context.isDefaultLauncher()
        hasUsageAccess = viewModel.hasUsageAccess()
        precisionSwitchedOn = viewModel.isPrecisionSwitchedOn()
        viewModel.onShown()
        val away = System.currentTimeMillis() - leftAtMillis
        if (focus != null && leftAtMillis > 0 && away >= WELCOME_BACK_AFTER_MILLIS) welcomeBack = true
        onPauseOrDispose { leftAtMillis = System.currentTimeMillis() }
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

    // Opening an app from STILL goes: blocked? → daily limit used up? → its own limits? → gated? →
    // "what for?" → launch delay → "how long?" → open.
    fun openApp(app: LauncherApp) {
        if (viewModel.launch(app)) {
            query = ""
            openFolderId = null
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

    fun afterPause(app: LauncherApp) {
        if (app.hasLaunchDelay) launchDelayKey = app.key else afterLaunchDelay(app)
    }

    fun launchApp(app: LauncherApp) {
        // Read live, as Precision mode reads it, so both agree on whether the app's own limits keep it closed.
        val appClosed = viewModel.appLimitStatus(app)?.closed
        when {
            viewModel.focusLocks(app) -> focusLockedKey = app.key
            app.isBlocked -> blockedScreenKey = app.key
            app.isLimited -> limitScreenKey = app.key
            appClosed != null -> {
                appLimitReason = if (appClosed == AppClosed.DONE_TODAY) AppLimitReason.DONE_TODAY else AppLimitReason.WAITING
                appLimitScreenKey = app.key
            }
            app.isGated -> gateScreenKey = app.key
            else -> {
                val due = viewModel.pauseFor(app)
                if (due != null) {
                    pause = due
                    pauseKey = app.key
                } else {
                    afterPause(app)
                }
            }
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
        gateScreenKey = null
        emergencyKey = null
        limitScreenKey = null
        limitEmergencyKey = null
        appLimitSheetKey = null
        focusLockedKey = null
        appLimitScreenKey = null
        pauseKey = null
        momentsOpen = false
        quietMinute = false
        folderPickerKey = null
        newFolderForKey = null
        folderMenuId = null
        renamingFolderId = null
        openFolderId = null
        precisionHelp = null
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
                InterventionKind.FOCUS -> focusLockedKey = app.key
                InterventionKind.BLOCKED -> {
                    // A block that just started (a schedule, or time's up) can take a moment to
                    // reach the app list; the blocked screen closes itself if it isn't there.
                    viewModel.setNow(Instant.now())
                    withTimeoutOrNull(BLOCK_SHOWS_WITHIN_MILLIS) {
                        viewModel.uiState.first { current -> current.apps.any { it.key == app.key && it.isBlocked } }
                    }
                    blockedScreenKey = app.key
                }
                InterventionKind.LIMIT -> {
                    viewModel.setNow(Instant.now())
                    val limited = withTimeoutOrNull(BLOCK_SHOWS_WITHIN_MILLIS) {
                        viewModel.uiState.first { current -> current.apps.any { it.key == app.key && it.isLimited } }
                    }
                    if (limited != null) limitScreenKey = app.key else launchApp(app)
                }
                // The app's own limits are read live in launchApp, so it shows the screen Precision mode asked for.
                InterventionKind.APP_LIMIT -> {
                    viewModel.setNow(Instant.now())
                    launchApp(app)
                }
                InterventionKind.VISIT_UP -> {
                    visitUpMinutes = request.plannedMinutes
                    appLimitReason = AppLimitReason.VISIT_UP
                    appLimitScreenKey = app.key
                }
                InterventionKind.GATED -> {
                    viewModel.setNow(Instant.now())
                    val gated = withTimeoutOrNull(BLOCK_SHOWS_WITHIN_MILLIS) {
                        viewModel.uiState.first { current -> current.apps.any { it.key == app.key && it.isGated } }
                    }
                    // If the gate has opened in the meantime, carry on as if opened from here.
                    if (gated != null) gateScreenKey = app.key else launchApp(app)
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

    // The held-back count in the notification shade was tapped.
    LaunchedEffect(heldNotificationRequests) {
        heldNotificationRequests.collect {
            // Held-back notifications are part of Insights, which focus mode keeps closed.
            if (viewModel.uiState.value.focusModeSince != null) return@collect
            closeAll()
            goHome(animate = false)
            overlay = Overlay.HELD_NOTIFICATIONS
        }
    }

    // Text shared to "Import as plan": read into the plan editor over the Plans tab.
    LaunchedEffect(listRequests) {
        listRequests.collect { text ->
            // Focus mode keeps the left page closed, so there's nowhere to add it to.
            if (viewModel.uiState.value.focusModeSince != null) return@collect
            closeAll()
            leftTab = LeftTab.PLANS
            pagerState.scrollToPage(Page.Productivity.ordinal)
            editor = EditorTarget.PasteList(text)
        }
    }

    // A launcher has nowhere to go back to: Back only returns to the home page.
    BackHandler {
        if (pagerState.currentPage != Page.Home.ordinal) goHome(animate = true)
    }

    // The pages sit on the wallpaper; screens opened over them keep solid backgrounds.
    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize()) {
        WallpaperBackground()
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (Page.entries[page]) {
                Page.Productivity -> {
                    when (leftTab) {
                        LeftTab.TODAY -> TodayPage(
                            tabs = { LeftPageTabs(leftTab, compact = true) { leftTab = it } },
                            onOpenEditor = { editor = it },
                            locks = state.gateLocks.appsByItem,
                            viewModel = todayViewModel,
                        )
                        LeftTab.NOTES -> NotesPage(tabs = { LeftPageTabs(leftTab) { leftTab = it } }, onOpenEditor = { editor = it })
                        LeftTab.PLANS -> PlansPage(tabs = { LeftPageTabs(leftTab) { leftTab = it } }, onOpenEditor = { editor = it })
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
                    progress = agenda?.progress.orEmpty(),
                    limit = state.limit,
                    lockedApps = state.gateLocks.lockedAppCount,
                    itemsToGo = state.gateLocks.itemsToGo,
                    focus = focus,
                    onDueTasksClick = {
                        leftTab = LeftTab.TODAY
                        scope.launch { pagerState.animateScrollToPage(Page.Productivity.ordinal) }
                    },
                    reminders = activeReminders,
                    today = now.toLocalDate(),
                    onReminderDone = todayViewModel::completeReminder,
                    onReminderDismiss = todayViewModel::dismissReminder,
                    onLaunch = ::launchApp,
                    // In focus mode home's apps open, but menus, Insights, Customise and gestures don't.
                    onLongPress = { if (!focusOn) menuAppKey = it.key },
                    onSwipeUp = { if (!focusOn) overlay = Overlay.INSIGHTS },
                    onDoubleTap = if (state.doubleTapToLock) {
                        { if (!viewModel.lockScreen()) precisionHelp = PrecisionHelp.LOCK }
                    } else {
                        null
                    },
                    onSwipeDown = if (state.swipeDownNotifications && !focusOn) {
                        { if (!viewModel.openNotifications()) precisionHelp = PrecisionHelp.NOTIFICATIONS }
                    } else {
                        null
                    },
                    phoneButton = state.phoneButton.app,
                    cameraButton = state.cameraButton.app,
                    keynote = keynote.keynote?.let { HomeKeynote(it.quality, keynote.moments.size) },
                    onKeynoteClick = { momentsOpen = true },
                    unlocks = keynote.unlocks,
                    cards = run {
                        val returning = focus?.takeIf { welcomeBack }
                        val asking = keynote.enabled && (keynote.asking || changingKeynote)
                        if (returning == null && !asking) {
                            null
                        } else {
                            {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    returning?.let { session ->
                                        WelcomeBackCard(
                                            session = session,
                                            onBackToIt = { welcomeBack = false },
                                            onEnd = {
                                                todayViewModel.stopTimer()
                                                welcomeBack = false
                                            },
                                        )
                                    }
                                    if (asking) {
                                        KeynoteCard(
                                            initial = if (changingKeynote) keynote.keynote else keynote.previous,
                                            linkOptions = linkOptions,
                                            changing = changingKeynote,
                                            onStart = { quality, action, link ->
                                                keynoteViewModel.startDay(quality, action, link)
                                                changingKeynote = false
                                            },
                                            onPutOff = { if (changingKeynote) changingKeynote = false else keynoteViewModel.putOff() },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    precisionLost = precisionLost,
                    onTurnOnPrecision = { overlay = Overlay.PRECISION },
                    onDismissPrecisionLost = viewModel::forgetPrecision,
                    folders = state.folders,
                    folderStyle = state.folderStyle,
                    folderOpening = state.folderOpening,
                    appDisplay = state.appDisplay,
                    openFolderId = openFolderId,
                    onOpenFolder = { openFolderId = it },
                    onFolderLongPress = { if (!focusOn) folderMenuId = it.id },
                    onLongPressHome = { if (!focusOn) overlay = Overlay.HOME_CUSTOMISE },
                    onReorderFavorites = { if (!focusOn) viewModel.reorderFavorites(it) },
                    focusSince = state.focusModeSince,
                    onFocusHold = {
                        val starting = !focusOn
                        closeAll()
                        viewModel.toggleFocusMode()
                        // Without Precision mode, apps opened from Recents or notifications get past it.
                        if (starting && !precisionRunning) precisionHelp = PrecisionHelp.FOCUS
                    },
                )
                Page.Apps -> AppsPage(
                    apps = state.apps,
                    recentlyInstalled = state.recentlyInstalled,
                    query = query,
                    onQueryChange = { query = it },
                    isCurrentPage = pagerState.currentPage == Page.Apps.ordinal,
                    // Once the swipe has settled, so the keyboard doesn't rise mid-swipe.
                    openKeyboard = state.appListKeyboard && pagerState.settledPage == Page.Apps.ordinal,
                    fadeWithUse = state.appListFade,
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
                heldCount = held.size.takeIf { filterSettings.on },
                onOpenHeld = { overlay = Overlay.HELD_NOTIFICATIONS },
                onOpenSettings = { overlay = Overlay.SETTINGS },
                onOpenDailyLimit = { overlay = Overlay.DAILY_LIMIT },
                onAllowUsageAccess = { context.startActivity(viewModel.usageAccessIntent()) },
                onClose = { overlay = null },
            )
            // Long-pressing home opens Customise on its own; closing it goes back to home.
            Overlay.HOME_CUSTOMISE -> CustomisePage(onBack = { overlay = null })
            Overlay.DAILY_LIMIT -> DailyLimitPage(
                apps = state.apps,
                hasUsageAccess = hasUsageAccess,
                onAllowUsageAccess = { context.startActivity(viewModel.usageAccessIntent()) },
                onBack = { overlay = Overlay.INSIGHTS },
            )
            Overlay.HELD_NOTIFICATIONS -> HeldNotificationsPage(
                apps = state.apps,
                onOpenApp = ::launchApp,
                onOpenSettings = { overlay = Overlay.NOTIFICATION_FILTER },
                onClose = { overlay = Overlay.INSIGHTS },
            )
            Overlay.SETTINGS, Overlay.CUSTOMISE, Overlay.HIDDEN_APPS, Overlay.BLOCKED_APPS, Overlay.APP_LIMITS, Overlay.SCHEDULES, Overlay.GATES,
            Overlay.NOTIFICATION_FILTER, Overlay.PRECISION, Overlay.SHORT_VIDEOS -> SettingsScreen(
                page = when (current) {
                    Overlay.CUSTOMISE -> SettingsPage.CUSTOMISE
                    Overlay.HIDDEN_APPS -> SettingsPage.HIDDEN_APPS
                    Overlay.BLOCKED_APPS -> SettingsPage.BLOCKED_APPS
                    Overlay.APP_LIMITS -> SettingsPage.APP_LIMITS
                    Overlay.SCHEDULES -> SettingsPage.SCHEDULES
                    Overlay.GATES -> SettingsPage.GATES
                    Overlay.NOTIFICATION_FILTER -> SettingsPage.NOTIFICATION_FILTER
                    Overlay.PRECISION -> SettingsPage.PRECISION
                    Overlay.SHORT_VIDEOS -> SettingsPage.SHORT_VIDEOS
                    else -> SettingsPage.MAIN
                },
                onNavigate = { page ->
                    overlay = when (page) {
                        SettingsPage.MAIN -> Overlay.SETTINGS
                        SettingsPage.CUSTOMISE -> Overlay.CUSTOMISE
                        SettingsPage.HIDDEN_APPS -> Overlay.HIDDEN_APPS
                        SettingsPage.BLOCKED_APPS -> Overlay.BLOCKED_APPS
                        SettingsPage.APP_LIMITS -> Overlay.APP_LIMITS
                        SettingsPage.SCHEDULES -> Overlay.SCHEDULES
                        SettingsPage.GATES -> Overlay.GATES
                        SettingsPage.NOTIFICATION_FILTER -> Overlay.NOTIFICATION_FILTER
                        SettingsPage.PRECISION -> Overlay.PRECISION
                        SettingsPage.SHORT_VIDEOS -> Overlay.SHORT_VIDEOS
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
                onOpenEditor = { editor = it },
                launchDelaySeconds = state.launchDelaySeconds,
                onSetLaunchDelaySeconds = viewModel::setLaunchDelaySeconds,
                timeUpBlockMinutes = state.timeUpBlockMinutes,
                onSetTimeUpBlockMinutes = viewModel::setTimeUpBlockMinutes,
                precisionOn = precisionRunning,
                precisionStuck = precisionSwitchedOn && !precisionRunning,
                onTurnOnPrecision = { context.startActivity(viewModel.precisionSettingsIntent()) },
                onTurnOffPrecision = viewModel::turnOffPrecision,
                onOpenAppInfo = { context.startActivity(viewModel.appInfoIntent()) },
                doubleTapToLock = state.doubleTapToLock,
                onSetDoubleTapToLock = viewModel::setDoubleTapToLock,
                swipeDownNotifications = state.swipeDownNotifications,
                onSetSwipeDownNotifications = viewModel::setSwipeDownNotifications,
                appListKeyboard = state.appListKeyboard,
                onSetAppListKeyboard = viewModel::setAppListKeyboard,
                appListFade = state.appListFade,
                onSetAppListFade = viewModel::setAppListFade,
                appLimits = state.appLimits,
                limitDay = state.limitDay,
                onEditAppLimit = { appLimitSheetKey = it.key },
                dailyQuality = state.dailyQuality,
                onSetDailyQuality = viewModel::setDailyQuality,
                unlocksOnHome = state.unlocksOnHome,
                onSetUnlocksOnHome = viewModel::setUnlocksOnHome,
                askWhyOn = state.askWhyOn,
                onSetAskWhyOn = viewModel::setAskWhyOn,
                onOpenHeldNotifications = { overlay = Overlay.HELD_NOTIFICATIONS },
            )
        }
    }

    // Editors cover everything else, including Settings when a schedule is opened from there.
    // Their own Back handler takes priority over the pages'.
    editor?.let { target -> EditorHost(target, onClose = { editor = null }, onOpen = { editor = it }) }

    // Focus mode keeps every app off home closed; it closes itself when focus mode ends.
    focusLockedApp?.let { app ->
        if (!focusOn) {
            LaunchedEffect(app.key) { focusLockedKey = null }
        } else {
            FocusLockedScreen(app.label, onClose = { focusLockedKey = null })
        }
    }

    // Opening a blocked app lands here instead; it closes itself when the block runs out.
    blockedScreenApp?.let { app ->
        val until = app.blockedUntil
        if (until == null) {
            LaunchedEffect(app.key) { blockedScreenKey = null }
        } else {
            BlockedScreen(app.label, until, app.blockedBySchedule, onClose = { blockedScreenKey = null })
        }
    }

    // The day's phone limit is used up: only allowed apps open. If it no longer applies (a new
    // day, or the limit changed), carry on opening the app.
    limitScreenApp?.let { app ->
        val limit = state.limit
        if (!app.isLimited || limit == null) {
            LaunchedEffect(app.key) {
                limitScreenKey = null
                launchApp(app)
            }
        } else {
            LimitScreen(
                app = app,
                limit = limit,
                allowedApps = state.apps.filter { it.component.packageName in state.limitSettings.allowed && !it.isWorkProfile },
                emergenciesLeft = state.limitSettings.emergenciesLeft(limit.day),
                onOpenAllowed = { allowed ->
                    limitScreenKey = null
                    launchApp(allowed)
                },
                onEmergency = {
                    limitScreenKey = null
                    limitEmergencyKey = app.key
                },
                onClose = { limitScreenKey = null },
            )
        }
    }

    // Emergency minutes past the limit wait through a countdown, then open the app for a few minutes.
    limitEmergencyApp?.let { app ->
        LaunchDelayScreen(
            appLabel = app.label,
            seconds = LimitSettings.EMERGENCY_DELAY_SECONDS,
            screenTime = null,
            question = stringResource(R.string.gate_emergency_question, app.label, LimitSettings.EMERGENCY_MINUTES),
            onOpen = {
                limitEmergencyKey = null
                viewModel.useLimitEmergency(app) { openApp(app) }
            },
            onCancel = { limitEmergencyKey = null },
        )
    }

    // An app's own limits keep it closed, or a visit ran its length. Nothing opens from here; the
    // screen goes once the reason does.
    appLimitScreenApp?.let { app ->
        val reason = appLimitReason
        val status = viewModel.appLimitStatus(app)
        val stillApplies = when (reason) {
            AppLimitReason.DONE_TODAY -> status?.closed == AppClosed.DONE_TODAY
            AppLimitReason.WAITING -> status?.closed == AppClosed.WAITING
            AppLimitReason.VISIT_UP -> true
            null -> false
        }
        if (reason == null || !stillApplies) {
            LaunchedEffect(app.key, reason) { appLimitScreenKey = null }
        } else {
            AppLimitScreen(
                appLabel = app.label,
                reason = reason,
                status = status,
                visitMinutes = visitUpMinutes,
                nextDay = dayStart(state.limitDay.plusDays(1), state.limitSettings.dayStartHour),
                onClose = { appLimitScreenKey = null },
            )
        }
    }

    // "What are you opening it for?", now and then, before an app whose limits ask.
    pauseApp?.let { app ->
        val current = pause
        if (current == null) {
            LaunchedEffect(app.key) { pauseKey = null }
        } else {
            LaunchedEffect(app.key) { viewModel.onPauseShown(app) }
            WhyPauseScreen(
                app = app,
                pause = current,
                quality = keynote.keynote?.quality,
                onTurnBack = { answer ->
                    viewModel.recordChoice(app, ChoiceSource.PAUSE, answer, turnedBack = true)
                    pauseKey = null
                    pause = null
                },
                onOpen = { answer ->
                    viewModel.recordChoice(app, ChoiceSource.PAUSE, answer, turnedBack = false)
                    pauseKey = null
                    pause = null
                    afterPause(app)
                },
                onQuietMinute = { answer ->
                    // Not opening it counts as turning back.
                    viewModel.recordChoice(app, ChoiceSource.PAUSE, answer, turnedBack = true)
                    pauseKey = null
                    pause = null
                    quietMinute = true
                },
            )
        }
    }

    // A minute to settle: from the pause before an app, or from today's quality.
    if (quietMinute) {
        QuietMinuteScreen(
            onChoose = { choice ->
                quietMinute = false
                when (choice) {
                    QuietChoice.SMALL_STEP -> {
                        leftTab = LeftTab.TODAY
                        scope.launch { pagerState.animateScrollToPage(Page.Productivity.ordinal) }
                    }
                    QuietChoice.BREAK -> goHome(animate = true)
                    QuietChoice.REACH_SOMEONE -> try {
                        context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        // No phone app; nothing to open.
                    }
                }
            },
            onClose = { quietMinute = false },
        )
    }

    // A task gate keeps the app closed until what's left is done on the Today page. If the gate
    // opens while this shows (its hours end), carry on opening the app.
    gateScreenApp?.let { app ->
        if (!app.isGated) {
            LaunchedEffect(app.key) {
                gateScreenKey = null
                launchApp(app)
            }
        } else {
            GateScreen(
                app = app,
                onEmergencyOpen = {
                    gateScreenKey = null
                    emergencyKey = app.key
                },
                onClose = { gateScreenKey = null },
            )
        }
    }

    // An emergency open waits, then lets the app past its gates for a few minutes.
    emergencyApp?.let { app ->
        val emergency = app.emergencyOpen
        if (emergency == null) {
            LaunchedEffect(app.key) { emergencyKey = null }
        } else {
            LaunchDelayScreen(
                appLabel = app.label,
                seconds = emergency.delaySeconds,
                screenTime = null,
                question = stringResource(R.string.gate_emergency_question, app.label, emergency.minutes),
                onOpen = {
                    emergencyKey = null
                    viewModel.emergencyOpen(app) { openApp(app) }
                },
                onCancel = { emergencyKey = null },
            )
        }
    }

    launchDelayApp?.let { app ->
        LaunchDelayScreen(
            appLabel = app.label,
            seconds = state.launchDelaySeconds,
            screenTime = screenTime,
            onOpen = {
                launchDelayKey = null
                viewModel.recordChoice(app, ChoiceSource.LAUNCH_DELAY, answer = null, turnedBack = false)
                afterLaunchDelay(app)
            },
            onCancel = {
                launchDelayKey = null
                viewModel.recordChoice(app, ChoiceSource.LAUNCH_DELAY, answer = null, turnedBack = true)
            },
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
            onLimits = {
                menuAppKey = null
                appLimitSheetKey = app.key
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
            folderName = state.folders.firstOrNull { it.id == app.folderId }?.name,
            onMoveToFolder = {
                menuAppKey = null
                if (state.folders.isEmpty()) newFolderForKey = app.key else folderPickerKey = app.key
            },
            onRemoveFromFolder = {
                viewModel.moveToFolder(app, null)
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

    // Tapping today's quality on home: what counted as practising it.
    val todayKeynote = keynote.keynote
    if (momentsOpen && todayKeynote != null) {
        MomentsSheet(
            keynote = todayKeynote,
            linkedTitle = keynote.linkedTitle,
            moments = keynote.moments,
            unlocks = keynote.unlocks,
            phoneToday = keynote.phoneToday,
            budgetApps = state.apps.filter { it.appLimit?.left != null && !it.isWorkProfile },
            labelOf = { packageName ->
                state.apps.firstOrNull { it.component.packageName == packageName && !it.isWorkProfile }?.label ?: packageName
            },
            onChange = {
                momentsOpen = false
                changingKeynote = true
            },
            onQuietMinute = {
                momentsOpen = false
                quietMinute = true
            },
            onDismiss = { momentsOpen = false },
        )
    }

    appLimitSheetApp?.let { app ->
        AppLimitSheet(
            app = app,
            entry = state.appLimits[app.component.packageName],
            day = state.limitDay,
            hasUsageAccess = hasUsageAccess,
            precisionOn = precisionRunning,
            askWhyOn = state.askWhyOn,
            onChange = { limit -> viewModel.setAppLimit(app, limit) },
            onDismiss = { appLimitSheetKey = null },
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

    precisionHelp?.let { help ->
        AlertDialog(
            onDismissRequest = { precisionHelp = null },
            title = {
                Text(
                    stringResource(
                        when (help) {
                            PrecisionHelp.LOCK -> R.string.lock_needs_precision_title
                            PrecisionHelp.NOTIFICATIONS -> R.string.notifications_needs_precision_title
                            PrecisionHelp.FOCUS -> R.string.focus_needs_precision_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        when (help) {
                            PrecisionHelp.LOCK -> R.string.lock_needs_precision_body
                            PrecisionHelp.NOTIFICATIONS -> R.string.notifications_needs_precision_body
                            PrecisionHelp.FOCUS -> R.string.focus_needs_precision_body
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        precisionHelp = null
                        overlay = Overlay.PRECISION
                    },
                ) { Text(stringResource(R.string.lock_set_up)) }
            },
            dismissButton = {
                TextButton(onClick = { precisionHelp = null }) { Text(stringResource(R.string.lock_not_now)) }
            },
        )
    }

    folderPickerApp?.let { app ->
        FolderPickerSheet(
            app = app,
            folders = state.folders,
            onMove = { folderId ->
                viewModel.moveToFolder(app, folderId)
                folderPickerKey = null
            },
            onNewFolder = {
                folderPickerKey = null
                newFolderForKey = app.key
            },
            onDismiss = { folderPickerKey = null },
        )
    }

    newFolderApp?.let { app ->
        FolderNameDialog(
            initial = "",
            onSave = { name ->
                viewModel.createFolder(name, app)
                newFolderForKey = null
            },
            onDismiss = { newFolderForKey = null },
        )
    }

    folderMenu?.let { folder ->
        FolderActionsSheet(
            folder = folder,
            onRename = {
                folderMenuId = null
                renamingFolderId = folder.id
            },
            onDelete = {
                viewModel.deleteFolder(folder.id)
                if (openFolderId == folder.id) openFolderId = null
                folderMenuId = null
            },
            onDismiss = { folderMenuId = null },
        )
    }

    renamingFolder?.let { folder ->
        FolderNameDialog(
            initial = folder.name,
            onSave = { name ->
                viewModel.renameFolder(folder.id, name)
                renamingFolderId = null
            },
            onDismiss = { renamingFolderId = null },
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

/** Away from STILL this long during a focus session, and home says welcome back. */
private const val WELCOME_BACK_AFTER_MILLIS = 30_000L

/** How long the Precision mode service gets to connect before home says it's off. */
private const val PRECISION_CONNECT_MILLIS = 3_000L

/** How long Precision mode's blocked screen waits for a block that just started to show up. */
private const val BLOCK_SHOWS_WITHIN_MILLIS = 2_000L

/** Gestures that need Precision mode, for the dialog shown when it's off. */
private enum class PrecisionHelp { LOCK, NOTIFICATIONS, FOCUS }
