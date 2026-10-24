package app.still.launcher.ui

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.BuildConfig
import app.still.launcher.StillApplication
import app.still.launcher.apps.recentlyInstalled
import app.still.launcher.blocking.activeBlock
import app.still.launcher.blocking.scheduledBlocks
import app.still.launcher.data.AccentColor
import app.still.launcher.data.AppDisplay
import app.still.launcher.data.AppIcons
import app.still.launcher.data.AppRepository
import app.still.launcher.data.FolderOpening
import app.still.launcher.data.FolderStyle
import app.still.launcher.data.AppScreenTime
import app.still.launcher.data.BlockingRepository
import app.still.launcher.data.CornerButton
import app.still.launcher.data.CornerChoice
import app.still.launcher.data.cornerApp
import app.still.launcher.data.GateRepository
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.LauncherSettings
import app.still.launcher.data.MonoIcon
import app.still.launcher.data.PracticeRepository
import app.still.launcher.data.practice.Choice
import app.still.launcher.data.practice.ChoiceAnswer
import app.still.launcher.data.practice.ChoiceSource
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.data.WallpaperChoice
import app.still.launcher.gates.EmergencyOpen
import app.still.launcher.gates.GateInputs
import app.still.launcher.gates.GateCheck
import app.still.launcher.gates.GateLocks
import app.still.launcher.gates.TaskGateMonitor
import app.still.launcher.gates.closedGates
import app.still.launcher.gates.emergencyOpenFor
import app.still.launcher.gates.gateLocks
import app.still.launcher.limit.AppLimit
import app.still.launcher.limit.AppLimitEntry
import app.still.launcher.limit.AppLimitMonitor
import app.still.launcher.limit.AppLimitStatus
import app.still.launcher.limit.AppVisit
import app.still.launcher.limit.DailyLimitMonitor
import app.still.launcher.limit.Pause
import app.still.launcher.limit.appLimitStatus
import app.still.launcher.limit.limitDay
import app.still.launcher.limit.withLimit
import app.still.launcher.limit.LimitSettings
import app.still.launcher.limit.LimitState
import app.still.launcher.precision.ForegroundRule
import app.still.launcher.precision.Intervention
import app.still.launcher.precision.PrecisionMode
import app.still.launcher.reminders.AppTimeReminders
import app.still.launcher.ui.theme.Appearance
import app.still.launcher.util.EssentialApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Collator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** An installed app with the user's customizations applied. */
data class LauncherApp(
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val originalLabel: String,
    val isWorkProfile: Boolean,
    val canUninstall: Boolean,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    /** False for apps STILL never blocks (phone, Settings, STILL). */
    val canBlock: Boolean = true,
    /** When a running block ends; null when the app isn't blocked right now. */
    val blockedUntil: Instant? = null,
    /** The blocking schedule behind [blockedUntil], or null for a block set by hand. */
    val blockedBySchedule: String? = null,
    /** Opening it from STILL waits through a countdown first. */
    val hasLaunchDelay: Boolean = false,
    /** Opening it from STILL asks how long you'll use it, then reminds you. */
    val hasTimeReminder: Boolean = false,
    /** False for apps task gates never close (see [EssentialApps.canGate]). */
    val canGate: Boolean = true,
    /** The task gates keeping it closed right now, with what's left to do; empty when none. */
    val gates: List<GateCheck> = emptyList(),
    /** How it can still be opened past [gates] today; null when it can't. */
    val emergencyOpen: EmergencyOpen? = null,
    /** The day's phone limit is used up and this isn't an allowed app. */
    val isLimited: Boolean = false,
    /** The home folder it's in, if any. */
    val folderId: String? = null,
    /** When it was first installed, in epoch milliseconds. */
    val installedAt: Long = 0L,
    /** Its time so far today, when STILL reads it; 0 for apps STILL never blocks. */
    val minutesToday: Int = 0,
    /** Where it stands against its own limits; null when it has none. */
    val appLimit: AppLimitStatus? = null,
) {
    val isRenamed: Boolean get() = label != originalLabel
    val isBlocked: Boolean get() = blockedUntil != null
    val isGated: Boolean get() = gates.isNotEmpty()

    /** Its own limits keep it closed: done for today, or waiting between visits. */
    val isAppClosed: Boolean get() = appLimit?.closed != null
}

/** What changes on its own, minute to minute: gates, the daily limit, time per app, and visits to limited apps. */
private data class LiveInputs(
    val gates: GateInputs?,
    val limit: LimitState?,
    val usage: Map<String, Long>,
    val visits: Map<String, AppVisit>,
)

/** A folder on home with its apps, in the order they were added; hidden and uninstalled apps are left out. */
data class HomeFolder(val id: String, val name: String, val apps: List<LauncherApp>)

/** One of home's corner buttons: what was chosen, the app it opens (null when off), and the phone's own app for it. */
data class HomeButton(val choice: CornerChoice = CornerChoice(), val app: LauncherApp? = null, val defaultApp: LauncherApp? = null)

data class LauncherUiState(
    /** All apps, hidden ones included, sorted by display label. */
    val apps: List<LauncherApp> = emptyList(),
    /** Apps installed in the last few days, newest first; hidden ones left out. */
    val recentlyInstalled: List<LauncherApp> = emptyList(),
    /** Home screen apps in the order the user added them. */
    val favorites: List<LauncherApp> = emptyList(),
    val launchDelaySeconds: Int = LauncherSettings.DEFAULT_LAUNCH_DELAY_SECONDS,
    /** 0 only reminds when time's up; otherwise the app is blocked this many minutes. */
    val timeUpBlockMinutes: Int = 0,
    val doubleTapToLock: Boolean = true,
    val appListKeyboard: Boolean = false,
    val appListFade: Boolean = true,
    val swipeDownNotifications: Boolean = true,
    val phoneButton: HomeButton = HomeButton(),
    val cameraButton: HomeButton = HomeButton(),
    /** Precision mode was turned on, so home says so if it goes off. */
    val precisionWanted: Boolean = false,
    /** What task gates keep closed right now, and which items keep them closed. */
    val gateLocks: GateLocks = GateLocks(),
    /** Apps the user keeps open whatever the gates say. */
    val gateAlwaysOpen: Set<String> = emptySet(),
    /** Today against the daily limit; null without a limit or Usage access. */
    val limit: LimitState? = null,
    val limitSettings: LimitSettings = LimitSettings(),
    val folders: List<HomeFolder> = emptyList(),
    val folderStyle: FolderStyle = FolderStyle.STRIP,
    val folderOpening: FolderOpening = FolderOpening.SPOTLIGHT,
    val appDisplay: AppDisplay = AppDisplay.TEXT,
    /** Each app's own limits as saved, and the day they're read for. */
    val appLimits: Map<String, AppLimitEntry> = emptyMap(),
    val limitDay: LocalDate = LocalDate.now(),
    /** Inner practice switches. */
    val dailyQuality: Boolean = true,
    val unlocksOnHome: Boolean = true,
    val askWhyOn: Boolean = true,
)

class LauncherViewModel(
    private val appRepository: AppRepository,
    private val preferences: LauncherPreferences,
    private val blocking: BlockingRepository,
    private val screenTime: ScreenTimeRepository,
    private val essentialApps: EssentialApps,
    private val appTimeReminders: AppTimeReminders,
    private val precision: PrecisionMode,
    private val taskGates: TaskGateMonitor,
    private val gateRepository: GateRepository,
    private val dailyLimit: DailyLimitMonitor,
    private val appIcons: AppIcons,
    private val appLimits: AppLimitMonitor,
    private val practice: PracticeRepository,
) : ViewModel() {

    /** The current minute, so blocks end on time without anything else changing. */
    private val clock = MutableStateFlow(Instant.now())

    val uiState: StateFlow<LauncherUiState> =
        combine(
            appRepository.installedApps,
            preferences.settings,
            combine(blocking.blocks, blocking.schedules, ::Pair),
            combine(taskGates.inputs, dailyLimit.state, dailyLimit.appUsage, appLimits.visits, ::LiveInputs),
            clock,
        ) { installed, settings, (blocks, schedules), (gateInputs, limitState, usage, visits), now ->
            val collator = Collator.getInstance()
            val zone = ZoneId.systemDefault()
            val scheduled = scheduledBlocks(schedules, LocalDateTime.ofInstant(now, zone))
            val alwaysOpen = settings.gateAlwaysOpenPackages
            val canGate = { packageName: String -> essentialApps.canGate(packageName, alwaysOpen) }
            val closed = gateInputs?.let { closedGates(it, now, zone, canGate) }.orEmpty()
            val today = now.atZone(zone).toLocalDate()
            val limitDay = limitDay(LocalDateTime.ofInstant(now, zone), settings.limit.dayStartHour)
            val folderOf = settings.folders.flatMap { folder -> folder.apps.map { it to folder.id } }.toMap()
            val apps = installed
                .map { app ->
                    val packageName = app.component.packageName
                    val canBlock = !essentialApps.isEssential(packageName)
                    // Essential apps are never blocked, whatever a schedule lists.
                    val block = if (canBlock) activeBlock(packageName, blocks, scheduled, now, zone) else null
                    val gates = closed[packageName].orEmpty()
                    LauncherApp(
                        key = app.key,
                        component = app.component,
                        user = app.user,
                        label = settings.renames[app.key] ?: app.label,
                        originalLabel = app.label,
                        isWorkProfile = app.isWorkProfile,
                        canUninstall = app.canUninstall,
                        isFavorite = app.key in settings.favorites,
                        isHidden = app.key in settings.hidden,
                        canBlock = canBlock,
                        blockedUntil = block?.until,
                        blockedBySchedule = block?.scheduleName,
                        hasLaunchDelay = canBlock && packageName in settings.launchDelayPackages,
                        hasTimeReminder = canBlock && packageName in settings.timeReminderPackages,
                        canGate = canBlock && canGate(packageName),
                        gates = gates,
                        emergencyOpen = gateInputs?.let { emergencyOpenFor(gates, it.events, today, zone) },
                        isLimited = canBlock &&
                            settings.limit.keepsClosed(limitState, packageName, now.toEpochMilli(), essentialApps::alwaysOpenAfterLimit),
                        folderId = folderOf[app.key],
                        installedAt = app.firstInstallTime,
                        minutesToday = if (canBlock) Duration.ofMillis(usage[packageName] ?: 0L).toMinutes().toInt() else 0,
                        appLimit = if (!canBlock) null else settings.appLimits[packageName]?.let { entry ->
                            appLimitStatus(entry, limitDay, usage[packageName] ?: 0L, visits[packageName], now.toEpochMilli(), precision.gate.graceMillis)
                        },
                    )
                }
                .sortedWith(compareBy(collator) { it.label })
            val byKey = apps.associateBy { it.key }
            fun homeButton(choice: CornerChoice, defaultPackage: String?): HomeButton {
                // The phone's own app for it, in the personal profile.
                val default = apps.firstOrNull { it.component.packageName == defaultPackage && !it.isWorkProfile }
                return HomeButton(choice, cornerApp(choice, choice.appKey?.let(byKey::get), default), default)
            }
            // Gates cover a package in every profile; name it after the personal app, which comes
            // last here so it wins.
            val labels = apps.sortedByDescending { it.isWorkProfile }.associate { it.component.packageName to it.label }
            LauncherUiState(
                apps = apps,
                recentlyInstalled = recentlyInstalled(apps.filterNot { it.isHidden }, now.toEpochMilli()) { it.installedAt },
                favorites = settings.favorites.mapNotNull { byKey[it] }.filterNot { it.isHidden },
                launchDelaySeconds = settings.launchDelaySeconds,
                timeUpBlockMinutes = settings.timeUpBlockMinutes,
                doubleTapToLock = settings.doubleTapToLock,
                appListKeyboard = settings.appListKeyboard,
                appListFade = settings.appListFade,
                swipeDownNotifications = settings.swipeDownNotifications,
                phoneButton = homeButton(settings.phoneButton, essentialApps.phoneApp()),
                cameraButton = homeButton(settings.cameraButton, essentialApps.cameraApp()),
                precisionWanted = settings.precisionWanted,
                gateLocks = gateLocks(closed) { labels[it] },
                gateAlwaysOpen = alwaysOpen,
                limit = limitState,
                limitSettings = settings.limit,
                folders = settings.folders.map { folder ->
                    HomeFolder(folder.id, folder.name, folder.apps.mapNotNull { key -> byKey[key]?.takeUnless { it.isHidden } })
                },
                folderStyle = settings.folderStyle,
                folderOpening = settings.folderOpening,
                appDisplay = settings.appDisplay,
                appLimits = settings.appLimits,
                limitDay = limitDay,
                dailyQuality = settings.dailyQuality,
                unlocksOnHome = settings.unlocksOnHome,
                askWhyOn = settings.askWhyOn,
            )
        }
            .flowOn(Dispatchers.Default)
            // A launcher stays in memory; keep the list warm instead of reloading on every return.
            .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState())

    /** The UI reports the time every minute. */
    fun setNow(now: Instant) {
        clock.value = now
        taskGates.setToday(now.atZone(ZoneId.systemDefault()).toLocalDate())
        viewModelScope.launch { dailyLimit.refresh() }
    }

    /** Accent and wallpaper, for the theme around everything. */
    val appearance: StateFlow<Appearance> = preferences.settings
        .map { Appearance(it.accent, it.wallpaper, it.wallpaperDim) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())

    /** Whether Precision mode's service is connected. */
    val precisionRunning: StateFlow<Boolean> = precision.running

    /** Precision mode caught an app coming to the front, and STILL should step in. */
    val interventions: Flow<Intervention> = precision.interventions.receiveAsFlow()

    fun launch(app: LauncherApp): Boolean {
        val now = System.currentTimeMillis()
        // STILL has done its checks, so Precision mode lets the app come to the front.
        precision.gate.allow(app.component.packageName, now + precision.gate.graceMillis)
        val launched = appRepository.launch(app.component, app.user)
        if (launched) appLimits.onOpened(app.component.packageName, app.label, now)
        return launched
    }

    /**
     * STILL is on screen again, so whatever app was in front has left and any visit has ended.
     * Precision mode is told directly, rather than trusting it saw STILL's window come up.
     */
    fun onShown() {
        val now = System.currentTimeMillis()
        precision.gate.onForeground(BuildConfig.APPLICATION_ID, now, ForegroundRule.NONE)
        appLimits.onForeground(BuildConfig.APPLICATION_ID, now)
        appLimits.onLauncherShown(now)
    }

    /** Saves [limit] for [app]: tightening counts now, loosening waits for tomorrow. */
    fun setAppLimit(app: LauncherApp, limit: AppLimit) {
        if (!app.canBlock) return
        val day = uiState.value.limitDay
        viewModelScope.launch { preferences.updateAppLimit(app.component.packageName) { it.withLimit(limit, day) } }
    }

    /** Where [app] stands against its own limits right now, read the way Precision mode reads it; null when it has none. */
    fun appLimitStatus(app: LauncherApp): AppLimitStatus? = if (app.canBlock) appLimits.status(app.component.packageName) else null

    /** The pause to show before opening [app] now, or null to open it without asking. */
    fun pauseFor(app: LauncherApp): Pause? = appLimits.pauseFor(app.component.packageName)

    fun onPauseShown(app: LauncherApp) {
        appLimits.onAsked(app.component.packageName, System.currentTimeMillis())
    }

    /** Keeps what happened when STILL paused before [app]: it opened, or you turned back. */
    fun recordChoice(app: LauncherApp, source: ChoiceSource, answer: ChoiceAnswer?, turnedBack: Boolean) {
        val choice = Choice(at = Instant.now(), packageName = app.component.packageName, source = source, answer = answer, turnedBack = turnedBack)
        viewModelScope.launch { practice.record(choice) }
    }

    fun setDailyQuality(on: Boolean) {
        viewModelScope.launch { preferences.setDailyQuality(on) }
    }

    fun setUnlocksOnHome(on: Boolean) {
        viewModelScope.launch { preferences.setUnlocksOnHome(on) }
    }

    fun setAskWhyOn(on: Boolean) {
        viewModelScope.launch { preferences.setAskWhyOn(on) }
    }

    fun setFavorite(app: LauncherApp, favorite: Boolean) {
        viewModelScope.launch { preferences.setFavorite(app.key, favorite) }
    }

    /** Saves the order home's favourites were dragged into. */
    fun reorderFavorites(order: List<LauncherApp>) {
        viewModelScope.launch { preferences.setFavoriteOrder(order.map { it.key }) }
    }

    fun setHidden(app: LauncherApp, hidden: Boolean) {
        viewModelScope.launch {
            preferences.setHidden(app.key, hidden)
            if (hidden) preferences.setFavorite(app.key, favorite = false)
        }
    }

    /** Saving the original name, or a blank one, removes the rename. */
    fun rename(app: LauncherApp, label: String) {
        val newLabel = label.trim().takeIf { it.isNotEmpty() && it != app.originalLabel }
        viewModelScope.launch { preferences.setRename(app.key, newLabel) }
    }

    fun openAppInfo(app: LauncherApp) = appRepository.openAppInfo(app.component, app.user)

    fun uninstall(app: LauncherApp) = appRepository.uninstall(app.component.packageName, app.user)

    fun hasUsageAccess(): Boolean = screenTime.hasAccess()

    fun usageAccessIntent(): Intent = screenTime.accessSettingsIntent()

    /** Today's and the last week's time in [app]; null without Usage access. */
    suspend fun screenTime(app: LauncherApp): AppScreenTime? = screenTime.screenTime(app.component.packageName)

    fun setLaunchDelay(app: LauncherApp, on: Boolean) {
        viewModelScope.launch { preferences.setLaunchDelay(app.component.packageName, on) }
    }

    fun setTimeReminder(app: LauncherApp, on: Boolean) {
        viewModelScope.launch { preferences.setTimeReminder(app.component.packageName, on) }
    }

    fun setLaunchDelaySeconds(seconds: Int) {
        viewModelScope.launch { preferences.setLaunchDelaySeconds(seconds) }
    }

    fun setTimeUpBlockMinutes(minutes: Int) {
        viewModelScope.launch { preferences.setTimeUpBlockMinutes(minutes) }
    }

    /** Starts the time reminder for [app], which is about to open, for [minutes]. */
    fun startTimeSession(app: LauncherApp, minutes: Int) {
        appTimeReminders.start(app.component.packageName, app.label, minutes)
    }

    /**
     * Lets [app] past its closed gates for a few minutes, using up one of today's emergency
     * opens, then calls [onOpen].
     */
    fun emergencyOpen(app: LauncherApp, onOpen: () -> Unit) {
        val emergency = app.emergencyOpen ?: return
        val packageName = app.component.packageName
        viewModelScope.launch {
            // Whole milliseconds, as stored, so the saved event can be recognised below.
            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            gateRepository.recordEmergencyOpen(packageName, app.gates.map { it.gate }, now, now.plusSeconds(emergency.minutes * 60L))
            // Wait until the gates know, so Precision mode doesn't close the app again as it opens.
            withTimeoutOrNull(EMERGENCY_SAVE_TIMEOUT_MILLIS) {
                taskGates.inputs.first { inputs -> inputs?.events?.any { it.packageName == packageName && it.time == now } == true }
            }
            onOpen()
        }
    }

    /** Lets [app] past the used-up daily limit for a few minutes, using one of today's emergency openings, then calls [onOpen]. */
    fun useLimitEmergency(app: LauncherApp, onOpen: () -> Unit) {
        viewModelScope.launch {
            dailyLimit.useEmergency(app.component.packageName)
            onOpen()
        }
    }

    /** [app]'s one-colour icon, [sizePx] square; null when its icon can't be read. */
    suspend fun icon(app: LauncherApp, sizePx: Int): MonoIcon? = appIcons.monochrome(app.component, app.user, sizePx)

    /** Makes a folder named [name] and puts [app] in it. */
    fun createFolder(name: String, app: LauncherApp) {
        viewModelScope.launch { preferences.createFolder(name, app.key) }
    }

    /** Moves [app] into the folder [folderId], or out of its folder when that's null. */
    fun moveToFolder(app: LauncherApp, folderId: String?) {
        viewModelScope.launch { preferences.moveToFolder(app.key, folderId) }
    }

    fun renameFolder(folderId: String, name: String) {
        viewModelScope.launch { preferences.renameFolder(folderId, name) }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch { preferences.deleteFolder(folderId) }
    }

    fun setFolderStyle(style: FolderStyle) {
        viewModelScope.launch { preferences.setFolderStyle(style) }
    }

    fun setFolderOpening(opening: FolderOpening) {
        viewModelScope.launch { preferences.setFolderOpening(opening) }
    }

    fun setAppDisplay(display: AppDisplay) {
        viewModelScope.launch { preferences.setAppDisplay(display) }
    }

    fun setAccent(accent: AccentColor) {
        viewModelScope.launch { preferences.setAccent(accent) }
    }

    fun setWallpaper(wallpaper: WallpaperChoice) {
        viewModelScope.launch { preferences.setWallpaper(wallpaper) }
    }

    fun setWallpaperDim(dim: Float) {
        viewModelScope.launch { preferences.setWallpaperDim(dim) }
    }

    /** Android's wallpaper picker; on a Pixel, Wallpaper and style. */
    fun wallpaperPickerIntent(): Intent = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null)

    /** Locks the screen; false when Precision mode is off. */
    fun lockScreen(): Boolean = precision.lockScreen()

    fun openNotifications(): Boolean = precision.openNotifications()

    fun setGateAlwaysOpen(packages: Set<String>) {
        viewModelScope.launch { preferences.setGateAlwaysOpen(packages) }
    }

    fun setSwipeDownNotifications(on: Boolean) {
        viewModelScope.launch { preferences.setSwipeDownNotifications(on) }
    }

    fun setCornerButton(button: CornerButton, choice: CornerChoice) {
        viewModelScope.launch { preferences.setCornerButton(button, choice) }
    }

    fun setDoubleTapToLock(on: Boolean) {
        viewModelScope.launch { preferences.setDoubleTapToLock(on) }
    }

    fun setAppListKeyboard(on: Boolean) {
        viewModelScope.launch { preferences.setAppListKeyboard(on) }
    }

    fun setAppListFade(on: Boolean) {
        viewModelScope.launch { preferences.setAppListFade(on) }
    }

    fun precisionSettingsIntent(): Intent = precision.settingsIntent()

    /** Android has Precision mode switched on, whether or not it's running. */
    fun isPrecisionSwitchedOn(): Boolean = precision.isSwitchedOn()

    fun appInfoIntent(): Intent = precision.appInfoIntent()

    fun turnOffPrecision() {
        forgetPrecision()
        precision.turnOff()
    }

    /** Stops home from asking to turn Precision mode back on. */
    fun forgetPrecision() {
        viewModelScope.launch { preferences.setPrecisionWanted(false) }
    }

    /** Blocks [app] in every profile for [duration] from now. Essential apps are never blocked. */
    fun block(app: LauncherApp, duration: Duration) {
        if (!app.canBlock) return
        viewModelScope.launch {
            blocking.clearExpired()
            blocking.block(app.component.packageName, Instant.now().plus(duration))
        }
    }

    companion object {
        private const val EMERGENCY_SAVE_TIMEOUT_MILLIS = 2_000L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                LauncherViewModel(
                    container.appRepository,
                    container.preferences,
                    container.blockingRepository,
                    container.screenTimeRepository,
                    container.essentialApps,
                    container.appTimeReminders,
                    container.precisionMode,
                    container.taskGates,
                    container.gateRepository,
                    container.dailyLimit,
                    container.appIcons,
                    container.appLimits,
                    container.practiceRepository,
                )
            }
        }
    }
}
