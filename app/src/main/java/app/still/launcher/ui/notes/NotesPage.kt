package app.still.launcher.ui.notes

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.still.launcher.R
import app.still.launcher.data.notes.Note
import app.still.launcher.data.notes.checklistDone
import app.still.launcher.data.notes.displayTitle
import app.still.launcher.data.notes.previewLine
import app.still.launcher.ui.components.AddButton
import app.still.launcher.ui.editor.EditorTarget
import app.still.launcher.ui.productivity.ActionRow
import app.still.launcher.ui.productivity.ActionsSheet
import app.still.launcher.ui.productivity.DeleteConfirmDialog
import app.still.launcher.ui.productivity.SectionTitle

/** Left page, Notes tab. Tap a note to open it; long-press to pin or delete. */
@Composable
fun NotesPage(
    tabs: @Composable () -> Unit,
    onOpenEditor: (EditorTarget) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = viewModel(factory = NotesViewModel.Factory),
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var menuNote by remember { mutableStateOf<Note?>(null) }

    Box(modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 32.dp, top = 48.dp, end = 32.dp, bottom = 112.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Column {
                    tabs()
                    notes?.takeIf { it.isNotEmpty() }?.let { all ->
                        Text(
                            text = pluralStringResource(R.plurals.notes_count, all.size, all.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            val current = notes ?: return@LazyColumn
            if (current.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.notes_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            val (pinned, others) = current.partition { it.pinned }
            if (pinned.isNotEmpty()) {
                item(key = "pinned-title") { SectionTitle(R.string.notes_pinned) }
                items(pinned, key = { it.id }) { note ->
                    NoteRow(note, onOpen = { onOpenEditor(EditorTarget.EditNote(note.id)) }, onLongClick = { menuNote = note })
                }
            }
            if (others.isNotEmpty()) {
                // Only label the rest when there's a pinned group to tell them apart from.
                if (pinned.isNotEmpty()) item(key = "others-title") { SectionTitle(R.string.notes_others) }
                items(others, key = { it.id }) { note ->
                    NoteRow(note, onOpen = { onOpenEditor(EditorTarget.EditNote(note.id)) }, onLongClick = { menuNote = note })
                }
            }
        }

        AddButton(
            description = stringResource(R.string.notes_add),
            onClick = { onOpenEditor(EditorTarget.EditNote(null)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        )
    }

    menuNote?.let { note ->
        NoteActionsSheet(
            note = note,
            onDismiss = { menuNote = null },
            onSetPinned = { pinned ->
                viewModel.setPinned(note, pinned)
                menuNote = null
            },
            onDelete = {
                viewModel.delete(note)
                menuNote = null
            },
        )
    }
}

@Composable
private fun NoteRow(note: Note, onOpen: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val details = listOfNotNull(
        note.previewLine,
        note.checklist.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.note_checklist_progress, note.checklistDone, it.size)
        },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = note.displayTitle ?: stringResource(R.string.note_untitled),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NoteActionsSheet(
    note: Note,
    onDismiss: () -> Unit,
    onSetPinned: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val name = note.displayTitle ?: stringResource(R.string.note_untitled)
    ActionsSheet(title = name, onDismiss = onDismiss) {
        if (note.pinned) ActionRow(stringResource(R.string.note_unpin)) { onSetPinned(false) }
        else ActionRow(stringResource(R.string.note_pin)) { onSetPinned(true) }
        ActionRow(stringResource(R.string.note_delete)) { confirmDelete = true }
    }
    if (confirmDelete) {
        DeleteConfirmDialog(
            name = name,
            detail = stringResource(R.string.note_delete_detail),
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete,
        )
    }
}
