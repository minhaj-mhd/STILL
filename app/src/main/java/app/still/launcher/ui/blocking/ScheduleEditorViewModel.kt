package app.still.launcher.ui.blocking

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.BlockingRepository
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.data.blocking.BlockingSchedule
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import app.still.launcher.ui.editor.EditorTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

data class ScheduleDraft(
    val target: EditorTarget.EditSchedule,
    val original: BlockingSchedule?,
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val everyOtherWeek: Boolean = false,
    val packages: Set<String> = emptySet(),
) {
    /** A schedule needs at least one day and at least one app to be worth saving; a blank name gets a default. */
    val isComplete: Boolean get() = days.isNotEmpty() && packages.isNotEmpty()
}

class ScheduleEditorViewModel(
    private val repository: BlockingRepository,
    private val screenTime: ScreenTimeRepository,
) : ViewModel() {

    private val _draft = MutableStateFlow<ScheduleDraft?>(null)

    /** Null while loading. */
    val draft: StateFlow<ScheduleDraft?> = _draft

    private val _usage = MutableStateFlow<Map<String, Duration>>(emptyMap())

    /** Last week's screen time per package, for sorting the app picker; empty without Usage access. */
    val usage: StateFlow<Map<String, Duration>> = _usage

    // Saves and loads run one at a time, so reopening right after closing reads the saved version.
    private val mutex = Mutex()
    private var discarded = false
    private var defaultName = ""

    /** [defaultName] names a new schedule, and any schedule whose name is left blank. */
    fun open(target: EditorTarget.EditSchedule, defaultName: String) {
        _draft.value = null
        discarded = false
        this.defaultName = defaultName
        viewModelScope.launch {
            mutex.withLock {
                val saved = target.scheduleId?.let { repository.getSchedule(it) }
                _draft.value = saved?.toDraft(target) ?: newDraft(target, defaultName)
            }
        }
        viewModelScope.launch { _usage.value = screenTime.summary()?.lastSevenDays.orEmpty() }
    }

    fun update(change: (ScheduleDraft) -> ScheduleDraft) {
        _draft.update { it?.let(change) }
    }

    /**
     * Writes the draft when the editor closes. The editor asks before closing an incomplete
     * schedule, so skipping one here only happens on Home; it's logged to make that visible.
     */
    fun save() {
        val draft = _draft.value ?: return
        if (discarded) return
        if (!draft.isComplete) {
            Log.i(TAG, "Schedule not saved: ${if (draft.packages.isEmpty()) "no apps chosen" else "no days chosen"}")
            return
        }
        val named = draft.copy(name = draft.name.trim().ifEmpty { defaultName })
        viewModelScope.launch { mutex.withLock { repository.saveSchedule(named.toSchedule()) } }
    }

    /** Close without saving; an existing schedule keeps its last saved version. */
    fun discard() {
        discarded = true
    }

    fun delete() {
        discarded = true
        val scheduleId = _draft.value?.original?.id ?: return
        viewModelScope.launch { mutex.withLock { repository.deleteSchedule(scheduleId) } }
    }

    private fun newDraft(target: EditorTarget.EditSchedule, defaultName: String): ScheduleDraft {
        val template = target.template
        return ScheduleDraft(
            target = target,
            original = null,
            name = defaultName,
            startMinute = template?.startMinute ?: (21 * 60),
            endMinute = template?.endMinute ?: (7 * 60),
        )
    }

    companion object {
        private const val TAG = "ScheduleEditor"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                ScheduleEditorViewModel(container.blockingRepository, container.screenTimeRepository)
            }
        }
    }
}

private fun BlockingSchedule.toDraft(target: EditorTarget.EditSchedule): ScheduleDraft {
    val weekly = schedule.recurrence as? Recurrence.Weekly
    return ScheduleDraft(
        target = target,
        original = this,
        name = name,
        startMinute = startMinute,
        endMinute = endMinute,
        days = weekly?.days ?: DayOfWeek.entries.toSet(),
        everyOtherWeek = (weekly?.interval ?: 1) > 1,
        packages = packages,
    )
}

private fun ScheduleDraft.toSchedule(): BlockingSchedule {
    val recurrence = Recurrence.Weekly(days, interval = if (everyOtherWeek) 2 else 1)
    // A new schedule counts its weeks from this one; an edited one keeps its start and its days off.
    val schedule = original?.schedule?.copy(recurrence = recurrence) ?: Schedule(start = LocalDate.now(), recurrence = recurrence)
    return (original ?: BlockingSchedule(name = name, schedule = schedule, startMinute = startMinute, endMinute = endMinute, packages = packages))
        .copy(
            name = name.trim(),
            schedule = schedule,
            startMinute = startMinute,
            endMinute = endMinute,
            packages = packages,
        )
}
