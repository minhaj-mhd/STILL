package app.still.launcher.ui.notifications

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.notifications.HeldNotification
import app.still.launcher.notifications.NotificationFilter
import app.still.launcher.util.EssentialApps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The notification filter's settings, as the Settings page shows them. */
data class FilterSettings(val on: Boolean = false, val allowed: Set<String> = emptySet(), val countShown: Boolean = true)

class NotificationFilterViewModel(
    private val filter: NotificationFilter,
    private val preferences: LauncherPreferences,
    private val essentialApps: EssentialApps,
) : ViewModel() {

    val settings: StateFlow<FilterSettings> = preferences.settings
        .map { FilterSettings(it.notificationFilterOn, it.notificationAllowedPackages, it.notificationCountShown) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FilterSettings())

    /** Newest first. */
    val held: StateFlow<List<HeldNotification>> = filter.held.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val connected: StateFlow<Boolean> = filter.connected

    fun hasAccess(): Boolean = filter.hasAccess()

    fun accessSettingsIntent(): Intent = filter.accessSettingsIntent()

    /** Apps whose notifications always come through, so there's no point choosing them. */
    fun alwaysThrough(packageName: String): Boolean = essentialApps.notificationsAlwaysThrough(packageName)

    fun setOn(on: Boolean) {
        viewModelScope.launch { filter.setOn(on) }
    }

    fun setAllowed(packages: Set<String>) {
        viewModelScope.launch { preferences.setNotificationAllowed(packages) }
    }

    fun setCountShown(shown: Boolean) {
        viewModelScope.launch { preferences.setNotificationCountShown(shown) }
    }

    fun clearAll() {
        viewModelScope.launch { filter.clearAll() }
    }

    fun clear(packageName: String) {
        viewModelScope.launch { filter.clear(packageName) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                NotificationFilterViewModel(container.notificationFilter, container.preferences, container.essentialApps)
            }
        }
    }
}
