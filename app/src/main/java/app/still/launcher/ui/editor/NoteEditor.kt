package app.still.launcher.ui.editor

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
import app.still.launcher.ui.productivity.DeleteConfirmDialog

/** Title, free text and a checklist; saved when it closes, like the task and habit editors. */
@Composable
fun NoteEditor(
    target: EditorTarget.EditNote,
    onClose: () -> Unit,
    viewModel: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory),
) {
    LaunchedEffect(target) { viewModel.open(target) }
    DisposableEffect(Unit) { onDispose { viewModel.save() } }

    // The view model is shared between openings; ignore a draft left over from another note.
    val draft = viewModel.draft.collectAsStateWithLifecycle().value?.takeIf { it.target == target }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    EditorScaffold(
        title = stringResource(if (target.noteId == null) R.string.editor_new_note else R.string.editor_note),
        onDone = onClose,
    ) {
        if (draft == null) return@EditorScaffold

        TitleField(draft.title) { title -> viewModel.update { it.copy(title = title) } }
        NotesField(
            value = draft.text,
            onValueChange = { text -> viewModel.update { it.copy(text = text) } },
            placeholder = stringResource(R.string.note_text_hint),
            minLines = 6,
        )
        EditableChecklist(
            label = stringResource(R.string.note_checklist),
            addHint = stringResource(R.string.note_add_item),
            removeDescription = stringResource(R.string.note_remove_item),
            lines = draft.checklist,
            onChange = { lines -> viewModel.update { it.copy(checklist = lines) } },
            onAdd = viewModel::addChecklistItem,
        )
        SwitchRow(
            title = stringResource(R.string.note_pin),
            detail = null,
            checked = draft.pinned,
        ) { pinned -> viewModel.update { it.copy(pinned = pinned) } }

        if (draft.original != null) {
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.note_delete)) }
        }
    }

    if (confirmDelete && draft != null) {
        DeleteConfirmDialog(
            name = draft.title.ifBlank { stringResource(R.string.note_untitled) },
            detail = stringResource(R.string.note_delete_detail),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                viewModel.delete()
                onClose()
            },
        )
    }
}
