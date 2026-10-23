package app.still.launcher.ui

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.blocking.scheduledBlocks
import app.still.launcher.data.AppRepository
import app.still.launcher.data.AppScreenTime
import app.still.launcher.data.BlockingRepository
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.LauncherSettings
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.reminders.AppTimeReminders
import app.still.launcher.util.EssentialApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

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
) {
    val isRenamed: Boolean get() = label != originalLabel
    val isBlocked: Boolean get() = blockedUntil != null
}

data class LauncherUiState(
    /** All apps, hidden ones included, sorted by display label. */
    val apps: List<LauncherApp> = emptyList(),
    /** Home screen apps in the order the user added them. */
    val favorites: List<LauncherApp> = emptyList(),
    val launchDelaySeconds: Int = LauncherSettings.DEFAULT_LAUNCH_DELAY_SECONDS,
    /** 0 only reminds when time's up; otherwise the app is blocked this many minutes. */
    val timeUpBlockMinutes: Int = 0,
)

class LauncherViewModel(
    private val appRepository: AppRepository,
    private val preferences: LauncherPreferences,
    private val blocking: BlockingRepository,
    private val screenTime: ScreenTimeRepository,
    private val essentialApps: EssentialApps,
    private val appTimeReminders: AppTimeReminders,
) : ViewModel() {

    /** The current minute, so blocks end on time without anything else changing. */
    private val clock = MutableStateFlow(Instant.now())

    val uiState: StateFlow<LauncherUiState> =
        combine(
            appRepository.installedApps,
            preferences.settings,
            blocking.blocks,
            blocking.schedules,
            clock,
        ) { installed, settings, blocks, schedules, now ->
            val collator = Collator.getInstance()
            val zone = ZoneId.systemDefault()
            val scheduled = scheduledBlocks(schedules, LocalDateTime.ofInstant(now, zone))
            val apps = installed
                .map { app ->
                    val packageName = app.component.packageName
                    val canBlock = !essentialApps.isEssential(packageName)
                    // A block set by hand and a schedule can overlap; whichever ends later counts.
                    // Essential apps are never blocked, whatever a schedule lists.
                    val manualUntil = blocks[packageName]?.takeIf { canBlock && it.isAfter(now) }
                    val schedule = scheduled[packageName]?.takeIf { canBlock }
                    val scheduleUntil = schedule?.until?.atZone(zone)?.toInstant()
                    val bySchedule = scheduleUntil != null && (manualUntil == null || scheduleUntil > manualUntil)
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
                        blockedUntil = if (bySchedule) scheduleUntil else manualUntil,
                        blockedBySchedule = schedule?.scheduleName?.takeIf { bySchedule },
                        hasLaunchDelay = canBlock && packageName in settings.launchDelayPackages,
                        hasTimeReminder = canBlock && packageName in settings.timeReminderPackages,
                    )
                }
                .sortedWith(compareBy(collator) { it.label })
            val byKey = apps.associateBy { it.key }
            LauncherUiState(
                apps = apps,
                favorites = settings.favorites.mapNotNull { byKey[it] }.filterNot { it.isHidden },
                launchDelaySeconds = settings.launchDelaySeconds,
                timeUpBlockMinutes = settings.timeUpBlockMinutes,
            )
        }
            .flowOn(Dispatchers.Default)
            // A launcher stays in memory; keep the list warm instead of reloading on every return.
            .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState())

    /** The UI reports the time every minute. */
    fun setNow(now: Instant) {
        clock.value = now
    }

    fun launch(app: LauncherApp): Boolean = appRepository.launch(app.component, app.user)

    fun setFavorite(app: LauncherApp, favorite: Boolean) {
        viewModelScope.launch { preferences.setFavorite(app.key, favorite) }
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

    /** Blocks [app] in every profile for [duration] from now. Essential apps are never blocked. */
    fun block(app: LauncherApp, duration: Duration) {
        if (!app.canBlock) return
        viewModelScope.launch {
            blocking.clearExpired()
            blocking.block(app.component.packageName, Instant.now().plus(duration))
        }
    }

    companion object {
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
                )
            }
        }
    }
}
