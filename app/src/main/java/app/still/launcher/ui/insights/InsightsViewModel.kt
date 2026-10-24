package app.still.launcher.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.PracticeRepository
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.data.ScreenTimeSummary
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.keynote.UnlockMonitor
import app.still.launcher.keynote.WeekReflection
import app.still.launcher.keynote.perDay
import app.still.launcher.keynote.qualityDays
import app.still.launcher.keynote.turnedBackOf
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
import java.time.ZoneId

/** A habit and its current streak, for the Insights page. */
data class HabitStreak(val habit: Habit, val stats: HabitStats)

/** Duration habits finished over the last week: by the focus timer, and logged by hand. */
data class FocusStats(val byTimer: Int, val byHand: Int)

class InsightsViewModel(
    private val screenTimeRepository: ScreenTimeRepository,
    productivity: ProductivityRepository,
    practice: PracticeRepository,
    private val unlocks: UnlockMonitor,
    private val preferences: LauncherPreferences,
) : ViewModel() {

    /** Unlocks a day this week and the week before, read when the page opens. */
    private val unlockWeeks = MutableStateFlow<Pair<Int?, Int?>>(null to null)

    /** The last seven days, today included, in plain facts. */
    val week: StateFlow<WeekReflection> = run {
        val today = LocalDate.now()
        val first = today.minusDays(WEEK_DAYS - 1)
        val zone = ZoneId.systemDefault()
        combine(
            practice.choices(first.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant()),
            practice.keynotes(first, today),
            combine(productivity.tasks, productivity.habits, productivity.records(first, today), ::Triple),
            unlockWeeks,
        ) { choices, keynotes, (tasks, habits, records), (unlocksNow, unlocksBefore) ->
            val habitsById = habits.associateBy { it.id }
            val repeating = records.count { record ->
                !record.skipped && when (record.itemType) {
                    ItemType.TASK -> record.value >= 1.0
                    ItemType.HABIT -> habitsById[record.itemId]?.let { record.value >= it.target } == true
                }
            }
            // One-time and undated tasks keep their completion on the task itself.
            val oneTime = tasks.count { task ->
                task.parentId == null && task.schedule?.isRecurring != true &&
                    task.completedAt?.atZone(zone)?.toLocalDate()?.let { it in first..today } == true
            }
            val (turnedBack, paused) = turnedBackOf(choices.map { it.turnedBack })
            WeekReflection(
                turnedBack = turnedBack,
                paused = paused,
                unlocksPerDay = unlocksNow,
                unlocksBeforePerDay = unlocksBefore,
                qualities = qualityDays(keynotes.map { it.quality }),
                finished = repeating + oneTime,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekReflection())
    }

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
        viewModelScope.launch {
            val counts = unlocks.dailyCounts((WEEK_DAYS * 2).toInt(), preferences.current().limit.dayStartHour) ?: return@launch
            unlockWeeks.value = perDay(counts.take(WEEK_DAYS.toInt())) to perDay(counts.drop(WEEK_DAYS.toInt()))
        }
    }

    companion object {
        private const val STREAK_LOOKBACK_DAYS = 366L
        private const val FOCUS_DAYS = 7L
        private const val WEEK_DAYS = 7L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                InsightsViewModel(
                    container.screenTimeRepository,
                    container.productivityRepository,
                    container.practiceRepository,
                    container.unlockMonitor,
                    container.preferences,
                )
            }
        }
    }
}
