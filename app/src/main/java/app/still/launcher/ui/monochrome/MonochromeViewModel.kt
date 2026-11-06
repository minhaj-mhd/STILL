package app.still.launcher.ui.monochrome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.monochrome.Monochrome
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Monochrome's settings, as its page shows them. */
data class MonochromeSettings(
    val forApps: Boolean = true,
    val apps: Set<String> = emptySet(),
    val forFocus: Boolean = true,
)

/** Settings > Monochrome. */
class MonochromeViewModel(
    private val monochrome: Monochrome,
    private val preferences: LauncherPreferences,
    /** Whether Precision mode is running, which tells monochrome the app in front. */
    val precisionRunning: StateFlow<Boolean>,
    private val packageName: String,
) : ViewModel() {

    val settings: StateFlow<MonochromeSettings> = preferences.settings
        .map { MonochromeSettings(it.monochromeForApps, it.monochromeApps, it.monochromeForFocus) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonochromeSettings())

    val grantCommand: String get() = Monochrome.grantCommand(packageName)

    /** Asked again each time the page shows, since it's allowed from a computer. */
    fun isAllowed(): Boolean = monochrome.isAllowed

    /** Goes grey straight away if it should, once STILL has just been allowed. */
    fun refresh() = monochrome.refresh()

    fun setForApps(on: Boolean) {
        viewModelScope.launch { preferences.setMonochromeForApps(on) }
    }

    fun setApps(packages: Set<String>) {
        viewModelScope.launch { preferences.setMonochromeApps(packages) }
    }

    fun setForFocus(on: Boolean) {
        viewModelScope.launch { preferences.setMonochromeForFocus(on) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as StillApplication
                val container = application.container
                MonochromeViewModel(container.monochrome, container.preferences, container.precisionMode.running, application.packageName)
            }
        }
    }
}
