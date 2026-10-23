package app.still.launcher.ui.limit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.limit.DailyLimitMonitor
import app.still.launcher.limit.LimitPlan
import app.still.launcher.limit.LimitSettings
import app.still.launcher.limit.LimitState
import app.still.launcher.limit.limitDay
import app.still.launcher.limit.withPlan
import app.still.launcher.limit.withRaiseRule
import app.still.launcher.util.EssentialApps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** The daily limit's settings and today's state, for the section at the top of Insights. */
class DailyLimitViewModel(
    private val preferences: LauncherPreferences,
    private val monitor: DailyLimitMonitor,
    private val essentialApps: EssentialApps,
) : ViewModel() {

    val settings: StateFlow<LimitSettings> = preferences.settings
        .map { it.limit }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LimitSettings())

    /** Null without a limit or Usage access. */
    val state: StateFlow<LimitState?> = monitor.state

    /** Apps that always open and never count, so there's no point allowing them. */
    fun alwaysOpen(packageName: String): Boolean = essentialApps.alwaysOpenAfterLimit(packageName)

    fun refresh() {
        viewModelScope.launch { monitor.refresh() }
    }

    /** Raising the limit may wait until tomorrow; see [withPlan]. */
    fun setLimit(weekdayMinutes: Int, weekendMinutes: Int?) = update { it.withPlan(LimitPlan(weekdayMinutes, weekendMinutes), today(it)) }

    fun setWarnMinutes(minutes: Int) = update { it.copy(warnMinutes = minutes) }

    fun setDayStartHour(hour: Int) = update { it.copy(dayStartHour = hour) }

    fun setAllowed(packages: Set<String>) = update { it.copy(allowed = packages) }

    fun setCountsAllowed(counts: Boolean) = update { it.copy(countsAllowed = counts) }

    /** Switching the rule off is a raise too, so it may only let go tomorrow; see [withRaiseRule]. */
    fun setRaiseRule(on: Boolean) = update { it.withRaiseRule(on, today(it)) }

    private fun today(settings: LimitSettings): LocalDate = limitDay(LocalDateTime.now(), settings.dayStartHour)

    private fun update(change: (LimitSettings) -> LimitSettings) {
        viewModelScope.launch { preferences.updateLimit(change) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                DailyLimitViewModel(container.preferences, container.dailyLimit, container.essentialApps)
            }
        }
    }
}
