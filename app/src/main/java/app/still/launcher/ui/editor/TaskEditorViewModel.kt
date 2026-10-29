package app.still.launcher.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.gates.GateItem
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.OccurrenceRecord
import app.still.launcher.data.productivity.Priority
import app.still.launcher.data.productivity.Task
import app.still.launcher.gates.EditGuard
import app.still.launcher.gates.PendingEdit
import app.still.launcher.productivity.buildAgenda
import app.still.launcher.schedule.MissedPolicy
import app.still.launcher.schedule.Recurrence
import app.still.launcher.schedule.Schedule
import app.still.launcher.schedule.followingStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    /** The plan a plan task belongs to, which sets its date. */
    val planName: String? = null,
) {
    /** Changing the date carries a repeat pattern along; removing it also removes time and repeat. */
    fun withDue(date: LocalDate?): TaskDraft =
        if (date == null) copy(due = null, time = null, recurrence = Recurrence.Once, until = null)
        else copy(due = date, recurrence = recurrence.followingStart(date), until = until?.takeIf { it >= date })
}

class TaskEditorViewModel(private val repository: ProductivityRepository, private val guard: EditGuard) : ViewModel() {

    private val _draft = MutableStateFlow<TaskDraft?>(null)

    /** Null while loading. */
    val draft: StateFlow<TaskDraft?> = _draft

    private val _pending = MutableStateFlow<PendingEdit<TaskDraft>?>(null)

    /** A change that would open apps this task keeps locked, waiting for the slide; null when there's none. */
    val pending: StateFlow<PendingEdit<TaskDraft>?> = _pending

    /** The task's own records, so its agenda can be rebuilt as edited. */
    private var itemRecords = emptyList<OccurrenceRecord>()

    /** Apps the slide already let this editing session open, so they aren't asked about twice. */
    private var confirmedOpens = emptySet<String>()

    // Saves and loads run one at a time, so reopening right after closing reads the saved version.
    private val mutex = Mutex()
    private var savedSubtaskIds = emptyList<Long>()
    private var nextNewKey = -1L
    private var discarded = false

    fun open(target: EditorTarget.EditTask) {
        _draft.value = null
        _pending.value = null
        confirmedOpens = emptySet()
        itemRecords = emptyList()
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
                    val today = LocalDate.now()
                    itemRecords = repository.getRecords(today.minusDays(RECORD_LOOKBACK_DAYS), today)
                        .filter { it.itemType == ItemType.TASK && it.itemId == task.id }
                    guard.load()
                    val plan = task.planId?.let { repository.getPlan(it) }
                    _draft.value = task.toDraft(target, subtasks).copy(planName = plan?.name)
                }
            }
        }
    }

    /** Applies [change], unless it would open apps the task keeps locked: that waits in [pending]. */
    fun update(change: (TaskDraft) -> TaskDraft) {
        val current = _draft.value ?: return
        val changed = change(current)
        val opened = opensApps(changed) - confirmedOpens
        if (opened.isEmpty()) _draft.value = changed else _pending.value = PendingEdit(changed, opened)
    }

    /** The slide was done: make the held-back change. */
    fun confirmPending() {
        val pending = _pending.value ?: return
        confirmedOpens = confirmedOpens + pending.opens
        _draft.value = pending.draft
        _pending.value = null
    }

    fun cancelPending() {
        _pending.value = null
    }

    private fun opensApps(draft: TaskDraft): Set<String> {
        val task = draft.original ?: return emptySet()
        return guard.opens(GateItem(ItemType.TASK, task.id)) { date -> buildAgenda(date, listOf(draft.toTask()), emptyList(), itemRecords) }
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
        /** Long enough for a yearly task to find its previous occurrence, as on the Today page. */
        private const val RECORD_LOOKBACK_DAYS = 366L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                TaskEditorViewModel(container.productivityRepository, EditGuard(container.taskGates, container.essentialApps, container.preferences))
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
