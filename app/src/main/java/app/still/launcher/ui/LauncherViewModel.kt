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
import app.still.launcher.data.AppRepository
import app.still.launcher.data.AppScreenTime
import app.still.launcher.data.BlockingRepository
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.ScreenTimeRepository
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
) {
    val isRenamed: Boolean get() = label != originalLabel
    val isBlocked: Boolean get() = blockedUntil != null
}

data class LauncherUiState(
    /** All apps, hidden ones included, sorted by display label. */
    val apps: List<LauncherApp> = emptyList(),
    /** Home screen apps in the order the user added them. */
    val favorites: List<LauncherApp> = emptyList(),
)

class LauncherViewModel(
    private val appRepository: AppRepository,
    private val preferences: LauncherPreferences,
    private val blocking: BlockingRepository,
    private val screenTime: ScreenTimeRepository,
    private val essentialApps: EssentialApps,
) : ViewModel() {

    /** The current minute, so blocks end on time without anything else changing. */
    private val clock = MutableStateFlow(Instant.now())

    val uiState: StateFlow<LauncherUiState> =
        combine(appRepository.installedApps, preferences.settings, blocking.blocks, clock) { installed, settings, blocks, now ->
            val collator = Collator.getInstance()
            val apps = installed
                .map { app ->
                    val packageName = app.component.packageName
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
                        canBlock = !essentialApps.isEssential(packageName),
                        blockedUntil = blocks[packageName]?.takeIf { it.isAfter(now) },
                    )
                }
                .sortedWith(compareBy(collator) { it.label })
            val byKey = apps.associateBy { it.key }
            LauncherUiState(
                apps = apps,
                favorites = settings.favorites.mapNotNull { byKey[it] }.filterNot { it.isHidden },
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
                )
            }
        }
    }
}
