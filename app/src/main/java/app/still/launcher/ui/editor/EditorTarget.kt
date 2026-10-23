package app.still.launcher.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import app.still.launcher.ui.blocking.ScheduleEditor
import app.still.launcher.ui.blocking.ScheduleTemplate
import app.still.launcher.ui.gates.GateEditor

/** Which item a full-screen editor is open for. A null id means a new item, prefilled with [title]. */
sealed interface EditorTarget {
    data class EditTask(val taskId: Long?, val title: String = "") : EditorTarget
    data class EditHabit(val habitId: Long?, val title: String = "") : EditorTarget
    data class EditNote(val noteId: Long?, val title: String = "") : EditorTarget

    /** A new schedule can start from a [template]. */
    data class EditSchedule(val scheduleId: Long?, val template: ScheduleTemplate? = null) : EditorTarget

    data class EditGate(val gateId: Long?) : EditorTarget
}

/** Keeps the open editor across rotation and process death, as "kind|id|title". */
val EditorTargetSaver = Saver<EditorTarget?, String>(
    save = { target ->
        when (target) {
            null -> null
            is EditorTarget.EditTask -> "task|${target.taskId ?: ""}|${target.title}"
            is EditorTarget.EditHabit -> "habit|${target.habitId ?: ""}|${target.title}"
            is EditorTarget.EditNote -> "note|${target.noteId ?: ""}|${target.title}"
            is EditorTarget.EditSchedule -> "schedule|${target.scheduleId ?: ""}|${target.template?.name.orEmpty()}"
            is EditorTarget.EditGate -> "gate|${target.gateId ?: ""}|"
        }
    },
    restore = { saved ->
        val (kind, id, title) = saved.split("|", limit = 3)
        when (kind) {
            "task" -> EditorTarget.EditTask(id.toLongOrNull(), title)
            "habit" -> EditorTarget.EditHabit(id.toLongOrNull(), title)
            "note" -> EditorTarget.EditNote(id.toLongOrNull(), title)
            "schedule" -> EditorTarget.EditSchedule(id.toLongOrNull(), ScheduleTemplate.entries.firstOrNull { it.name == title })
            "gate" -> EditorTarget.EditGate(id.toLongOrNull())
            else -> null
        }
    },
)

/**
 * Shows the editor for [target] over the launcher. Editors save themselves when they leave the
 * screen, so Back, Done and the Home button all keep what was typed.
 */
@Composable
fun EditorHost(target: EditorTarget, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    when (target) {
        is EditorTarget.EditTask -> TaskEditor(target, onClose)
        is EditorTarget.EditHabit -> HabitEditor(target, onClose)
        is EditorTarget.EditNote -> NoteEditor(target, onClose)
        is EditorTarget.EditSchedule -> ScheduleEditor(target, onClose)
        is EditorTarget.EditGate -> GateEditor(target, onClose)
    }
}
