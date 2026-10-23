package app.still.launcher.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.ui.components.Chip
import app.still.launcher.ui.components.ChipRow
import app.still.launcher.ui.components.DatePickerDialog
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.TimePickerDialog
import app.still.launcher.ui.components.formatShortDate
import app.still.launcher.ui.components.formatTime
import app.still.launcher.data.gates.GateItem
import app.still.launcher.data.productivity.ItemType
import app.still.launcher.ui.LauncherViewModel
import app.still.launcher.ui.productivity.DeleteItemConfirm
import app.still.launcher.ui.productivity.PriorityChips
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TaskEditor(
    target: EditorTarget.EditTask,
    onClose: () -> Unit,
    viewModel: TaskEditorViewModel = viewModel(factory = TaskEditorViewModel.Factory),
    launcherViewModel: LauncherViewModel = viewModel(factory = LauncherViewModel.Factory),
) {
    LaunchedEffect(target) { viewModel.open(target) }
    DisposableEffect(Unit) { onDispose { viewModel.save() } }

    // The view model is shared between openings; ignore a draft left over from another item.
    val draft = viewModel.draft.collectAsStateWithLifecycle().value?.takeIf { it.target == target }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    EditorScaffold(
        title = stringResource(if (target.taskId == null) R.string.editor_new_task else R.string.editor_task),
        onDone = onClose,
    ) {
        if (draft == null) return@EditorScaffold

        TitleField(draft.title) { title -> viewModel.update { it.copy(title = title) } }
        NotesField(draft.notes, onValueChange = { notes -> viewModel.update { it.copy(notes = notes) } })

        Column {
            FieldLabel(stringResource(R.string.add_priority))
            PriorityChips(draft.priority) { priority -> viewModel.update { it.copy(priority = priority) } }
        }

        Column {
            FieldLabel(stringResource(R.string.editor_date))
            val today = LocalDate.now()
            ChipRow {
                Chip(stringResource(R.string.add_when_today), draft.due == today) { viewModel.update { it.withDue(today) } }
                Chip(stringResource(R.string.add_when_tomorrow), draft.due == today.plusDays(1)) {
                    viewModel.update { it.withDue(today.plusDays(1)) }
                }
                val otherDate = draft.due?.takeIf { it != today && it != today.plusDays(1) }
                Chip(otherDate?.let { formatShortDate(it) } ?: stringResource(R.string.add_when_pick), otherDate != null) {
                    pickingDate = true
                }
                Chip(stringResource(R.string.editor_no_date), draft.due == null) { viewModel.update { it.withDue(null) } }
            }
        }

        val due = draft.due
        if (due != null) {
            Column {
                FieldLabel(stringResource(R.string.editor_time))
                ChipRow {
                    Chip(stringResource(R.string.editor_no_time), draft.time == null) { viewModel.update { it.copy(time = null) } }
                    Chip(draft.time?.let { formatTime(it) } ?: stringResource(R.string.editor_time), draft.time != null) {
                        pickingTime = true
                    }
                }
            }
            RepeatEditor(due, draft.recurrence, draft.until) { recurrence, until ->
                viewModel.update { it.copy(recurrence = recurrence, until = until) }
            }
            SwitchRow(
                title = stringResource(R.string.editor_keep_overdue),
                detail = stringResource(R.string.editor_keep_overdue_detail),
                checked = draft.keepOverdue,
            ) { keep -> viewModel.update { it.copy(keepOverdue = keep) } }
        }

        EditableChecklist(
            label = stringResource(R.string.editor_subtasks),
            addHint = stringResource(R.string.editor_add_subtask),
            removeDescription = stringResource(R.string.editor_remove_subtask),
            lines = draft.subtasks.map { ChecklistLine(it.key, it.title, it.done) },
            onChange = { lines ->
                viewModel.update { current ->
                    val byKey = current.subtasks.associateBy { it.key }
                    current.copy(subtasks = lines.mapNotNull { line -> byKey[line.key]?.copy(title = line.text, done = line.done) })
                }
            },
            onAdd = viewModel::addSubtask,
        )

        if (draft.original != null) {
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.item_delete_task)) }
        }
    }

    if (pickingDate) {
        DatePickerDialog(
            initial = draft?.due ?: LocalDate.now(),
            onDismiss = { pickingDate = false },
            onPick = { date ->
                viewModel.update { it.withDue(date) }
                pickingDate = false
            },
        )
    }
    if (pickingTime) {
        TimePickerDialog(
            initial = draft?.time ?: LocalTime.of(9, 0),
            onDismiss = { pickingTime = false },
            onPick = { time ->
                viewModel.update { it.copy(time = time) }
                pickingTime = false
            },
        )
    }
    if (confirmDelete && draft != null) {
        DeleteItemConfirm(
            name = draft.title,
            detail = stringResource(R.string.item_delete_task_detail),
            locks = launcherViewModel.uiState.collectAsStateWithLifecycle().value.gateLocks.appsByItem[GateItem(ItemType.TASK, target.taskId ?: 0)].orEmpty(),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                viewModel.delete()
                onClose()
            },
        )
    }
}
