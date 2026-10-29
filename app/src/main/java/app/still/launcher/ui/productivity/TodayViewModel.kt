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
import app.still.launcher.focus.FocusSession
import app.still.launcher.focus.FocusTimer
import app.still.launcher.productivity.ACTIVE_REMINDER_LOOKBACK_DAYS
import app.still.launcher.productivity.HabitEntry
import app.still.launcher.productivity.Reminder
import app.still.launcher.productivity.TaskEntry
import app.still.launcher.productivity.TodayAgenda
import app.still.launcher.productivity.activeReminders
import app.still.launcher.productivity.buildAgenda
import app.still.launcher.productivity.withPlanDates
import app.still.launcher.reminders.ReminderScheduler
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** How often a habit added from the quick-add sheet repeats; the full editor offers more. */
sealed interface QuickFrequency {
    data object Daily : QuickFrequency
    data class TimesPerWeek(val count: Int) : QuickFrequency
}

class TodayViewModel(
    private val repository: ProductivityRepository,
    private val reminders: ReminderScheduler,
    private val focusTimer: FocusTimer,
) : ViewModel() {

    /** The current minute, reported by the UI (see [setNow]). */
    private val now = MutableStateFlow(LocalDateTime.now())
    private val today = now.map { it.toLocalDate() }.distinctUntilChanged()

    /** The current minute, for the Today page's "now" mark. */
    val currentMinute: StateFlow<LocalDateTime> = now

    /** Whether anything has a time of day, i.e. whether reminder permissions matter. */
    val hasReminders: StateFlow<Boolean> = combine(repository.tasks, repository.habits) { tasks, habits ->
        tasks.any { it.completedAt == null && it.schedule?.times.orEmpty().isNotEmpty() } ||
            habits.any { it.schedule?.times.orEmpty().isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun canNotify(): Boolean = reminders.canNotify()

    fun canScheduleExactAlarms(): Boolean = reminders.canScheduleExact()

    /** Null until the database has been read once. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val agenda: StateFlow<TodayAgenda?> = today
        .flatMapLatest { date ->
            combine(
                repository.tasks,
                repository.habits,
                repository.records(date.minusDays(RECORD_LOOKBACK_DAYS), date),
                repository.plans,
            ) { tasks, habits, records, plans ->
                buildAgenda(date, tasks, habits, records, plans = plans)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Reminders that went off and haven't been dealt with, shown as cards on the home screen
     * until they're done, swiped away, or the item is removed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeReminders: StateFlow<List<Reminder>> = now
        .flatMapLatest { time ->
            val date = time.toLocalDate()
            combine(
                repository.tasks,
                repository.habits,
                repository.records(date.minusDays(ACTIVE_REMINDER_LOOKBACK_DAYS), date),
                repository.dismissedReminderKeys,
                repository.plans,
            ) { tasks, habits, records, dismissed, plans ->
                activeReminders(time, tasks.withPlanDates(plans, date), habits, records, dismissed)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The focus timer that's running, if any. */
    val focus: StateFlow<FocusSession?> = focusTimer.session

    /** The UI reports the time every minute and on resume, so the date and reminder cards stay current. */
    fun setNow(time: LocalDateTime) {
        now.value = time.withSecond(0).withNano(0)
        // Its alarm finishes the timer too; this covers an alarm Android has delayed.
        if (focus.value?.isDue(System.currentTimeMillis()) == true) viewModelScope.launch { focusTimer.finishIfDue() }
    }

    /** Starts the focus timer for what's left of a duration habit today. */
    fun startTimer(entry: HabitEntry) {
        viewModelScope.launch { focusTimer.start(entry.habit, entry.occurrence, entry.progress) }
    }

    fun stopTimer() {
        viewModelScope.launch { focusTimer.stop() }
    }

    /** "Done" on a home reminder card; works like the notification's Done button. */
    fun completeReminder(reminder: Reminder) {
        viewModelScope.launch { reminders.markDone(reminder) }
    }

    fun dismissReminder(reminder: Reminder) {
        viewModelScope.launch { reminders.dismiss(reminder) }
    }

    fun setTaskDone(entry: TaskEntry, done: Boolean) {
        viewModelScope.launch { repository.setTaskDone(entry.task, entry.occurrence, done) }
    }

    fun setSubtaskDone(subtask: Task, done: Boolean) {
        viewModelScope.launch { repository.setTaskDone(subtask, occurrence = null, done = done) }
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
                schedule = Schedule(start = now.value.toLocalDate(), recurrence = Recurrence.Daily()),
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
                TodayViewModel(container.productivityRepository, container.reminderScheduler, container.focusTimer)
            }
        }
    }
}
