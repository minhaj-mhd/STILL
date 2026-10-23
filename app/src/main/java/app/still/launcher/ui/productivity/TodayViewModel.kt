package app.still.launcher.ui.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.Priority
import app.still.launcher.data.productivity.Task
import app.still.launcher.productivity.HabitEntry
import app.still.launcher.productivity.TaskEntry
import app.still.launcher.productivity.TodayAgenda
import app.still.launcher.productivity.buildAgenda
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.QuotaPeriod
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** How often a habit added from the quick-add sheet repeats; the full editor offers more. */
sealed interface QuickFrequency {
    data object Daily : QuickFrequency
    data class TimesPerWeek(val count: Int) : QuickFrequency
}

class TodayViewModel(private val repository: ProductivityRepository) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    /** Null until the database has been read once. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val agenda: StateFlow<TodayAgenda?> = today
        .flatMapLatest { date ->
            combine(
                repository.tasks,
                repository.habits,
                repository.records(date.minusDays(RECORD_LOOKBACK_DAYS), date),
            ) { tasks, habits, records ->
                buildAgenda(date, tasks, habits, records)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The UI reports the date whenever it changes (midnight, time zone change, resume). */
    fun setToday(date: LocalDate) {
        today.value = date
    }

    fun setTaskDone(entry: TaskEntry, done: Boolean) {
        viewModelScope.launch { repository.setTaskDone(entry.task, entry.occurrence, done) }
    }

    fun setHabitProgress(entry: HabitEntry, value: Double) {
        viewModelScope.launch { repository.setHabitProgress(entry.habit, entry.occurrence, value) }
    }

    fun setSkipped(entry: TaskEntry, skipped: Boolean) {
        val occurrence = entry.occurrence ?: return
        viewModelScope.launch { repository.setSkipped(ItemType.TASK, entry.task.id, occurrence, skipped) }
    }

    fun setSkipped(entry: HabitEntry, skipped: Boolean) {
        viewModelScope.launch { repository.setSkipped(ItemType.HABIT, entry.habit.id, entry.occurrence, skipped) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { repository.deleteTask(task.id) }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch { repository.deleteHabit(habit.id) }
    }

    /** A null [due] adds a task without a date. */
    fun addTask(title: String, due: LocalDate?, priority: Priority) {
        val task = Task(title = title.trim(), priority = priority, schedule = due?.let { Schedule(start = it) })
        viewModelScope.launch { repository.addTask(task) }
    }

    fun addHabit(title: String, measure: HabitMeasure, target: Double, frequency: QuickFrequency) {
        val habit = when (frequency) {
            QuickFrequency.Daily -> Habit(
                title = title.trim(),
                measure = measure,
                target = target,
                schedule = Schedule(start = today.value, recurrence = Recurrence.Daily()),
            )
            is QuickFrequency.TimesPerWeek -> Habit(
                title = title.trim(),
                measure = measure,
                target = target,
                quota = Quota(frequency.count, QuotaPeriod.WEEK),
            )
        }
        viewModelScope.launch { repository.addHabit(habit) }
    }

    companion object {
        /** Long enough for a yearly task to find its previous occurrence. */
        private const val RECORD_LOOKBACK_DAYS = 366L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                TodayViewModel(container.productivityRepository)
            }
        }
    }
}
