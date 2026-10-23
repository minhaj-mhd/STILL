package app.still.launcher.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.productivity.Priority
import app.still.launcher.data.productivity.Task
import app.still.launcher.schedule.MissedPolicy
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import app.still.launcher.schedule.followingStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** A subtask being edited. [key] is the task id, or a negative number for one not saved yet. */
data class SubtaskDraft(
    val key: Long,
    val title: String,
    val done: Boolean,
    val original: Task? = null,
)

/** Everything the task editor can change, kept apart from the saved [original] until it closes. */
data class TaskDraft(
    val target: EditorTarget.EditTask,
    val original: Task?,
    val title: String,
    val notes: String = "",
    val priority: Priority = Priority.NONE,
    val due: LocalDate? = null,
    val time: LocalTime? = null,
    val recurrence: Recurrence = Recurrence.Once,
    val until: LocalDate? = null,
    val keepOverdue: Boolean = true,
    val subtasks: List<SubtaskDraft> = emptyList(),
) {
    /** Changing the date carries a repeat pattern along; removing it also removes time and repeat. */
    fun withDue(date: LocalDate?): TaskDraft =
        if (date == null) copy(due = null, time = null, recurrence = Recurrence.Once, until = null)
        else copy(due = date, recurrence = recurrence.followingStart(date), until = until?.takeIf { it >= date })
}

class TaskEditorViewModel(private val repository: ProductivityRepository) : ViewModel() {

    private val _draft = MutableStateFlow<TaskDraft?>(null)

    /** Null while loading. */
    val draft: StateFlow<TaskDraft?> = _draft

    // Saves and loads run one at a time, so reopening right after closing reads the saved version.
    private val mutex = Mutex()
    private var savedSubtaskIds = emptyList<Long>()
    private var nextNewKey = -1L
    private var discarded = false

    fun open(target: EditorTarget.EditTask) {
        _draft.value = null
        discarded = false
        viewModelScope.launch {
            mutex.withLock {
                val task = target.taskId?.let { repository.getTask(it) }
                if (task == null) {
                    savedSubtaskIds = emptyList()
                    _draft.value = TaskDraft(target, original = null, title = target.title, due = LocalDate.now())
                } else {
                    val subtasks = repository.getSubtasks(task.id)
                    savedSubtaskIds = subtasks.map { it.id }
                    _draft.value = task.toDraft(target, subtasks)
                }
            }
        }
    }

    fun update(change: (TaskDraft) -> TaskDraft) {
        _draft.update { it?.let(change) }
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        update { it.copy(subtasks = it.subtasks + SubtaskDraft(nextNewKey--, title.trim(), done = false)) }
    }

    /** Writes the draft. Called when the editor leaves the screen; a blank title saves nothing. */
    fun save() {
        val draft = _draft.value ?: return
        if (discarded || draft.title.isBlank()) return
        val kept = draft.subtasks.filter { it.title.isNotBlank() }
        val removed = savedSubtaskIds - kept.mapNotNull { it.original?.id }.toSet()
        viewModelScope.launch {
            mutex.withLock { repository.saveTask(draft.toTask(), kept.map { it.toTask() }, removed) }
        }
    }

    fun delete() {
        discarded = true
        val taskId = _draft.value?.original?.id ?: return
        viewModelScope.launch { mutex.withLock { repository.deleteTask(taskId) } }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                TaskEditorViewModel(container.productivityRepository)
            }
        }
    }
}

private fun Task.toDraft(target: EditorTarget.EditTask, subtasks: List<Task>) = TaskDraft(
    target = target,
    original = this,
    title = title,
    notes = notes,
    priority = priority,
    due = schedule?.start,
    time = schedule?.times?.firstOrNull(),
    recurrence = schedule?.recurrence ?: Recurrence.Once,
    until = schedule?.until,
    keepOverdue = missedPolicy == MissedPolicy.CARRY_OVER,
    subtasks = subtasks.map { SubtaskDraft(key = it.id, title = it.title, done = it.completedAt != null, original = it) },
)

private fun TaskDraft.toTask(): Task {
    val previous = original?.schedule
    val schedule = due?.let { start ->
        Schedule(
            start = start,
            recurrence = recurrence,
            until = until.takeIf { recurrence != Recurrence.Once },
            times = listOfNotNull(time),
            // Skipped days and pauses aren't edited here; keep them.
            skippedDates = previous?.skippedDates.orEmpty(),
            pauses = previous?.pauses.orEmpty(),
        )
    }
    return (original ?: Task(title = title)).copy(
        title = title.trim(),
        notes = notes.trim(),
        priority = priority,
        schedule = schedule,
        missedPolicy = if (keepOverdue) MissedPolicy.CARRY_OVER else MissedPolicy.MISS,
    )
}

private fun SubtaskDraft.toTask(): Task = (original ?: Task(title = title)).copy(
    title = title.trim(),
    completedAt = if (done) original?.completedAt ?: Instant.now() else null,
)
