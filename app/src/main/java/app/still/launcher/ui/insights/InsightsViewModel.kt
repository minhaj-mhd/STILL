package app.still.launcher.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.data.ScreenTimeSummary
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.productivity.HabitStats
import app.still.launcher.productivity.habitStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A habit and its current streak, for the Insights page. */
data class HabitStreak(val habit: Habit, val stats: HabitStats)

/** Duration habits finished over the last week: by the focus timer, and logged by hand. */
data class FocusStats(val byTimer: Int, val byHand: Int)

class InsightsViewModel(
    private val screenTimeRepository: ScreenTimeRepository,
    productivity: ProductivityRepository,
) : ViewModel() {

    private val _screenTime = MutableStateFlow<ScreenTimeSummary?>(null)

    /** Null until loaded, and while Usage access is off. */
    val screenTime: StateFlow<ScreenTimeSummary?> = _screenTime

    /** Habits with a running streak, longest first. Records cover a year, so longer streaks show as a year. */
    val streaks: StateFlow<List<HabitStreak>> = combine(
        productivity.habits,
        productivity.records(LocalDate.now().minusDays(STREAK_LOOKBACK_DAYS), LocalDate.now()),
    ) { habits, records ->
        val today = LocalDate.now()
        val byHabit = records.filter { it.itemType == ItemType.HABIT }.groupBy { it.itemId }
        habits
            .map { habit -> HabitStreak(habit, habitStats(habit, byHabit[habit.id].orEmpty(), today)) }
            .filter { it.stats.currentStreak > 0 }
            .sortedByDescending { it.stats.currentStreak }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null when no duration habit was finished in the last week. */
    val focusStats: StateFlow<FocusStats?> = combine(
        productivity.habits,
        productivity.records(LocalDate.now().minusDays(FOCUS_DAYS - 1), LocalDate.now()),
    ) { habits, records ->
        val durationHabits = habits.filter { it.measure == HabitMeasure.DURATION }.associateBy { it.id }
        val finished = records.filter { record ->
            record.itemType == ItemType.HABIT && !record.skipped &&
                durationHabits[record.itemId]?.let { record.value >= it.target } == true
        }
        FocusStats(byTimer = finished.count { it.viaTimer }, byHand = finished.count { !it.viaTimer })
            .takeIf { finished.isNotEmpty() }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun hasUsageAccess(): Boolean = screenTimeRepository.hasAccess()

    /** Reads screen time again; called when the page opens and when STILL comes back to the front. */
    fun refresh() {
        viewModelScope.launch { _screenTime.value = screenTimeRepository.summary() }
    }

    companion object {
        private const val STREAK_LOOKBACK_DAYS = 366L
        private const val FOCUS_DAYS = 7L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                InsightsViewModel(container.screenTimeRepository, container.productivityRepository)
            }
        }
    }
}
