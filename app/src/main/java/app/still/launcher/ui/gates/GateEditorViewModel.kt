package app.still.launcher.ui.gates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.GateRepository
import app.still.launcher.data.ProductivityRepository
import app.still.launcher.data.ScreenTimeRepository
import app.still.launcher.data.gates.GateCondition
import app.still.launcher.data.gates.GateItem
import app.still.launcher.data.gates.GateWindow
import app.still.launcher.data.gates.TaskGate
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.data.productivity.Priority
import app.still.launcher.ui.editor.EditorTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

data class GateDraft(
    val target: EditorTarget.EditGate,
    val original: TaskGate?,
    val name: String,
    val packages: Set<String> = emptySet(),
    val condition: GateCondition = GateCondition.PRIORITY,
    val minPriority: Priority = Priority.HIGH,
    val includeTasks: Boolean = true,
    val includeHabits: Boolean = true,
    val includeUndated: Boolean = false,
    val items: List<GateItem> = emptyList(),
    val requireAll: Boolean = true,
    val window: GateWindow? = null,
    val emergencyOpensPerDay: Int = TaskGate.DEFAULT_EMERGENCY_OPENS,
    val emergencyMinutes: Int = TaskGate.DEFAULT_EMERGENCY_MINUTES,
) {
    /** A gate needs apps to close and something to wait for; a blank name gets a default. */
    val isComplete: Boolean get() = packages.isNotEmpty() && when (condition) {
        GateCondition.PRIORITY -> includeTasks || includeHabits
        GateCondition.ITEMS -> items.isNotEmpty()
    }
}

/** A task or habit that can be chosen for a gate. */
data class GateChoice(val item: GateItem, val title: String)

class GateEditorViewModel(
    private val repository: GateRepository,
    productivity: ProductivityRepository,
    private val screenTime: ScreenTimeRepository,
) : ViewModel() {

    private val _draft = MutableStateFlow<GateDraft?>(null)

    /** Null while loading. */
    val draft: StateFlow<GateDraft?> = _draft

    private val _usage = MutableStateFlow<Map<String, Duration>>(emptyMap())

    /** Last week's screen time per package, for sorting the app picker; empty without Usage access. */
    val usage: StateFlow<Map<String, Duration>> = _usage

    /** Tasks still to do (recurring ones always) and habits, for choosing particular items. */
    val choices: StateFlow<List<GateChoice>> = combine(productivity.tasks, productivity.habits) { tasks, habits ->
        val openTasks = tasks.filter { it.parentId == null && !it.archived && (it.completedAt == null || it.schedule?.isRecurring == true) }
        openTasks.map { GateChoice(GateItem(ItemType.TASK, it.id), it.title) } +
            habits.filterNot { it.archived }.map { GateChoice(GateItem(ItemType.HABIT, it.id), it.title) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Saves and loads run one at a time, so reopening right after closing reads the saved version.
    private val mutex = Mutex()
    private var discarded = false
    private var defaultName = ""

    /** [defaultName] names a new gate, and any gate whose name is left blank. */
    fun open(target: EditorTarget.EditGate, defaultName: String) {
        _draft.value = null
        discarded = false
        this.defaultName = defaultName
        viewModelScope.launch {
            mutex.withLock {
                val saved = target.gateId?.let { repository.getGate(it) }
                _draft.value = saved?.toDraft(target) ?: GateDraft(target, original = null, name = defaultName)
            }
        }
        viewModelScope.launch { _usage.value = screenTime.summary()?.lastSevenDays.orEmpty() }
    }

    fun update(change: (GateDraft) -> GateDraft) {
        _draft.update { it?.let(change) }
    }

    /** Writes the draft when the editor closes; the editor asks before closing an incomplete gate. */
    fun save() {
        val draft = _draft.value ?: return
        if (discarded) return
        if (!draft.isComplete) {
            Log.i(TAG, "Gate not saved: ${if (draft.packages.isEmpty()) "no apps chosen" else "nothing to wait for"}")
            return
        }
        val named = draft.copy(name = draft.name.trim().ifEmpty { defaultName })
        viewModelScope.launch { mutex.withLock { repository.saveGate(named.toGate()) } }
    }

    /** Close without saving; an existing gate keeps its last saved version. */
    fun discard() {
        discarded = true
    }

    fun delete() {
        discarded = true
        val gateId = _draft.value?.original?.id ?: return
        viewModelScope.launch { mutex.withLock { repository.deleteGate(gateId) } }
    }

    companion object {
        private const val TAG = "GateEditor"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                GateEditorViewModel(container.gateRepository, container.productivityRepository, container.screenTimeRepository)
            }
        }
    }
}

private fun TaskGate.toDraft(target: EditorTarget.EditGate) = GateDraft(
    target = target,
    original = this,
    name = name,
    packages = packages,
    condition = condition,
    minPriority = minPriority,
    includeTasks = includeTasks,
    includeHabits = includeHabits,
    includeUndated = includeUndated,
    items = items,
    requireAll = requireAll,
    window = window,
    emergencyOpensPerDay = emergencyOpensPerDay,
    emergencyMinutes = emergencyMinutes,
)

private fun GateDraft.toGate(): TaskGate =
    (original ?: TaskGate(name = name, packages = packages)).copy(
        name = name,
        packages = packages,
        condition = condition,
        minPriority = minPriority,
        includeTasks = includeTasks,
        includeHabits = includeHabits,
        includeUndated = includeUndated,
        items = items,
        requireAll = requireAll,
        window = window,
        emergencyOpensPerDay = emergencyOpensPerDay,
        emergencyMinutes = emergencyMinutes,
    )
