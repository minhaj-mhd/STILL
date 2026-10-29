package app.still.launcher.ui.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.productivity.Plan
import app.still.launcher.data.productivity.PlanColor
import app.still.launcher.data.productivity.Task
import app.still.launcher.productivity.MAX_PASTED_SUBTASKS
import app.still.launcher.productivity.PastedList
import app.still.launcher.productivity.PlanCalendar
import app.still.launcher.productivity.parsePastedList
import app.still.launcher.productivity.planTasks
import app.still.launcher.schedule.Schedule
import app.still.launcher.ui.editor.EditorTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** A task in the plan editor. [original] is the saved task, or the pasted one with its notes and priority. */
data class PlanTaskDraft(
    val key: Long,
    val day: Int,
    val title: String,
    val times: List<LocalTime> = emptyList(),
    val original: Task? = null,
    val subtasks: List<Task> = emptyList(),
)

/** Everything the plan editor can change, kept apart from the saved [original] until it's saved. */
data class PlanDraft(
    val original: Plan?,
    val name: String = "",
    val start: LocalDate = LocalDate.now(),
    val weekdays: Int = Plan.EVERY_DAY,
    val movesAlong: Boolean = true,
    val color: PlanColor = PlanColor.TEAL,
    val tasks: List<PlanTaskDraft> = emptyList(),
    /** Days shown, empty ones included, so a day can be added before its first task. */
    val dayCount: Int = 1,
    /** What was pasted, for what couldn't be read and what plans leave out. */
    val pasted: PastedList? = null,
) {
    val canSave: Boolean get() = tasks.any { it.title.isNotBlank() }
}

/** Writes a new plan, reads a pasted one, or changes a saved one. Nothing is saved until [save]. */
class PlanEditorViewModel(private val repository: ProductivityRepository) : ViewModel() {

    private val _draft = MutableStateFlow<PlanDraft?>(null)
    val draft: StateFlow<PlanDraft?> = _draft

    private var target: EditorTarget? = null
    private var savedTaskIds: List<Long> = emptyList()

    /** New tasks get keys below zero; saved ones keep their ids. */
    private var nextKey = -1L

    fun open(target: EditorTarget) {
        if (this.target == target) return
        this.target = target
        when (target) {
            is EditorTarget.PasteList -> _draft.value = PlanDraft(original = null).withPasted(target.text)
            is EditorTarget.EditPlan -> {
                val planId = target.planId
                if (planId == null) {
                    _draft.value = PlanDraft(original = null, tasks = listOf(PlanTaskDraft(nextKey--, day = 1, title = "")))
                } else {
                    viewModelScope.launch { load(planId) }
                }
            }
            else -> Unit
        }
    }

    private suspend fun load(planId: Long) {
        val plan = repository.getPlan(planId) ?: return
        val tasks = repository.getPlanTasks(planId)
        savedTaskIds = tasks.map { it.id }
        val start = plan.start ?: tasks.mapNotNull { task -> task.schedule?.start?.minusDays((task.planDay ?: 1) - 1L) }.minOrNull() ?: LocalDate.now()
        val drafts = tasks.map { PlanTaskDraft(it.id, it.planDay ?: 1, it.title, it.schedule?.times.orEmpty(), original = it) }
        _draft.value = PlanDraft(
            original = plan,
            name = plan.name,
            start = start,
            weekdays = plan.weekdays,
            movesAlong = plan.movesAlong,
            color = plan.color,
            tasks = drafts,
            dayCount = drafts.maxOfOrNull { it.day } ?: 1,
        )
    }

    /** Forgets the draft once the editor has gone, so the next opening starts afresh. */
    fun close() {
        target = null
        savedTaskIds = emptyList()
        _draft.value = null
    }

    fun update(change: (PlanDraft) -> PlanDraft) {
        _draft.value = _draft.value?.let(change)
    }

    /** Reads [text] as a plan, replacing what's there, from the day it starts on. */
    fun readPasted(text: String) {
        update { it.withPasted(text) }
    }

    private fun PlanDraft.withPasted(text: String): PlanDraft {
        val list = parsePastedList(text)
        val today = LocalDate.now()
        val calendar = PlanCalendar(start, weekdays)
        val tasks = list.planTasks(calendar, today, Instant.EPOCH).map { (task, subtasks) ->
            PlanTaskDraft(nextKey--, task.planDay ?: 1, task.title, task.schedule?.times.orEmpty(), original = task, subtasks = subtasks)
        }
        return copy(
            name = name.ifBlank { list.name.orEmpty() },
            tasks = tasks,
            dayCount = tasks.maxOfOrNull { it.day } ?: 1,
            pasted = list,
        )
    }

    fun addTask(day: Int) {
        update { it.copy(tasks = it.tasks + PlanTaskDraft(nextKey--, day, title = "")) }
    }

    /** Saves the plan, then hands back its id. */
    fun save(onSaved: (Long) -> Unit) {
        val draft = _draft.value?.takeIf { it.canSave } ?: return
        val plan = (draft.original ?: Plan(name = "")).copy(
            name = draft.name.trim(),
            start = draft.start,
            weekdays = draft.weekdays,
            movesAlong = draft.movesAlong,
            color = draft.color,
        )
        val calendar = PlanCalendar(plan, draft.start)
        // Spaced so each new task's subtasks fit between it and the next, keeping the order shown.
        val base = Instant.now()
        val kept = draft.tasks.filter { it.title.isNotBlank() }.sortedBy { it.day }
        val tasks = kept.mapIndexed { index, item ->
            val createdAt = base.plusMillis(index * (MAX_PASTED_SUBTASKS + 1L))
            val stored = item.original?.takeIf { it.id != 0L }
            val task = (item.original ?: Task(title = item.title)).copy(
                title = item.title.trim(),
                planDay = item.day,
                schedule = Schedule(start = calendar.date(item.day) ?: stored?.schedule?.start ?: draft.start, times = item.times.distinct().sorted()),
                createdAt = stored?.createdAt ?: createdAt,
            )
            task to item.subtasks.mapIndexed { position, subtask -> subtask.copy(createdAt = createdAt.plusMillis(position + 1L)) }
        }
        viewModelScope.launch {
            if (draft.original == null) {
                onSaved(repository.addPlan(plan, tasks))
            } else {
                val removed = savedTaskIds - kept.mapNotNull { it.original?.id?.takeIf { id -> id != 0L } }.toSet()
                repository.savePlan(plan, tasks.map { it.first }, removed)
                onSaved(plan.id)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                PlanEditorViewModel(container.productivityRepository)
            }
        }
    }
}
