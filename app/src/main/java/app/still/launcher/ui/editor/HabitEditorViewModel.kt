package app.still.launcher.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.productivity.Habit
import app.still.launcher.data.productivity.HabitMeasure
import app.still.launcher.data.productivity.Priority
import app.still.launcher.productivity.HabitStats
import app.still.launcher.productivity.habitStats
import app.still.launcher.schedule.Quota
import app.still.launcher.schedule.QuotaPeriod
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import app.still.launcher.ui.productivity.formatAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** How often a habit happens, in the terms the editor offers. */
sealed interface HabitFrequency {
    data object Daily : HabitFrequency
    data class EveryNDays(val days: Int) : HabitFrequency
    data class SpecificDays(val days: Set<DayOfWeek>) : HabitFrequency
    data class PerWeek(val count: Int) : HabitFrequency
    data class PerMonth(val count: Int) : HabitFrequency

    /** A pattern the editor doesn't offer (e.g. monthly); kept unchanged unless another is chosen. */
    data class Custom(val recurrence: Recurrence) : HabitFrequency

    val isScheduled: Boolean get() = this !is PerWeek && this !is PerMonth
}

data class HabitDraft(
    val target: EditorTarget.EditHabit,
    val original: Habit?,
    val title: String,
    val notes: String = "",
    val priority: Priority = Priority.NONE,
    val measure: HabitMeasure = HabitMeasure.CHECK,
    val targetText: String = "1",
    val unit: String = "",
    val frequency: HabitFrequency = HabitFrequency.Daily,
    val start: LocalDate = LocalDate.now(),
    val times: List<LocalTime> = emptyList(),
) {
    /** The parsed target; null while the field doesn't hold a positive number. */
    val targetValue: Double?
        get() = if (measure == HabitMeasure.CHECK) 1.0 else targetText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
}

class HabitEditorViewModel(private val repository: ProductivityRepository) : ViewModel() {

    private val _draft = MutableStateFlow<HabitDraft?>(null)

    /** Null while loading. */
    val draft: StateFlow<HabitDraft?> = _draft

    private val savedHabit = MutableStateFlow<Habit?>(null)

    /** Streaks and history of the saved habit; null for a new one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: StateFlow<HabitStats?> = savedHabit
        .flatMapLatest { habit ->
            if (habit == null) flowOf(null)
            else repository.habitRecords(habit.id).map { records -> habitStats(habit, records, LocalDate.now()) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Saves and loads run one at a time, so reopening right after closing reads the saved version.
    private val mutex = Mutex()
    private var discarded = false

    fun open(target: EditorTarget.EditHabit) {
        _draft.value = null
        savedHabit.value = null
        discarded = false
        viewModelScope.launch {
            mutex.withLock {
                val habit = target.habitId?.let { repository.getHabit(it) }
                savedHabit.value = habit
                _draft.value = habit?.toDraft(target) ?: HabitDraft(target, original = null, title = target.title)
            }
        }
    }

    fun update(change: (HabitDraft) -> HabitDraft) {
        _draft.update { it?.let(change) }
    }

    /** Writes the draft. Called when the editor leaves the screen; an incomplete draft saves nothing. */
    fun save() {
        val draft = _draft.value ?: return
        if (discarded || draft.title.isBlank()) return
        val habit = draft.toHabit()
        viewModelScope.launch { mutex.withLock { repository.saveHabit(habit) } }
    }

    fun delete() {
        discarded = true
        val habitId = _draft.value?.original?.id ?: return
        viewModelScope.launch { mutex.withLock { repository.deleteHabit(habitId) } }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                HabitEditorViewModel(container.productivityRepository)
            }
        }
    }
}

private fun Habit.toDraft(target: EditorTarget.EditHabit): HabitDraft {
    val quota = quota
    val recurrence = schedule?.recurrence
    val frequency = when {
        recurrence == null && quota != null ->
            if (quota.period == QuotaPeriod.WEEK) HabitFrequency.PerWeek(quota.count) else HabitFrequency.PerMonth(quota.count)
        recurrence is Recurrence.Daily ->
            if (recurrence.interval == 1) HabitFrequency.Daily else HabitFrequency.EveryNDays(recurrence.interval)
        recurrence is Recurrence.Weekly && recurrence.interval == 1 -> HabitFrequency.SpecificDays(recurrence.days)
        recurrence != null -> HabitFrequency.Custom(recurrence)
        else -> HabitFrequency.Daily
    }
    return HabitDraft(
        target = target,
        original = this,
        title = title,
        notes = notes,
        priority = priority,
        measure = measure,
        targetText = formatAmount(this.target),
        unit = unit.orEmpty(),
        frequency = frequency,
        start = schedule?.start ?: LocalDate.now(),
        times = schedule?.times.orEmpty(),
    )
}

private fun HabitDraft.toHabit(): Habit {
    val previous = original?.schedule
    fun scheduleOf(recurrence: Recurrence) = Schedule(
        start = start,
        recurrence = recurrence,
        until = previous?.until,
        times = times.distinct().sorted(),
        // Skipped days and pauses aren't edited here; keep them.
        skippedDates = previous?.skippedDates.orEmpty(),
        pauses = previous?.pauses.orEmpty(),
    )
    val (schedule, quota) = when (frequency) {
        HabitFrequency.Daily -> scheduleOf(Recurrence.Daily()) to null
        is HabitFrequency.EveryNDays -> scheduleOf(Recurrence.Daily(frequency.days)) to null
        is HabitFrequency.SpecificDays ->
            scheduleOf(Recurrence.Weekly(frequency.days.ifEmpty { setOf(start.dayOfWeek) })) to null
        is HabitFrequency.PerWeek -> null to Quota(frequency.count, QuotaPeriod.WEEK)
        is HabitFrequency.PerMonth -> null to Quota(frequency.count, QuotaPeriod.MONTH)
        is HabitFrequency.Custom -> scheduleOf(frequency.recurrence) to null
    }
    val base = original ?: Habit(title = title, schedule = schedule, quota = quota)
    return base.copy(
        title = title.trim(),
        notes = notes.trim(),
        priority = priority,
        measure = measure,
        target = targetValue ?: original?.target ?: 1.0,
        unit = unit.trim().takeIf { measure == HabitMeasure.MEASUREMENT && it.isNotEmpty() },
        schedule = schedule,
        quota = quota,
    )
}
