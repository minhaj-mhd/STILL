package app.still.launcher.ui

import android.content.ComponentName
import android.os.UserHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.AppRepository
import app.still.launcher.data.LauncherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

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
) {
    val isRenamed: Boolean get() = label != originalLabel
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
) : ViewModel() {

    val uiState: StateFlow<LauncherUiState> =
        combine(appRepository.installedApps, preferences.settings) { installed, settings ->
            val collator = Collator.getInstance()
            val apps = installed
                .map { app ->
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

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                LauncherViewModel(container.appRepository, container.preferences)
            }
        }
    }
}
