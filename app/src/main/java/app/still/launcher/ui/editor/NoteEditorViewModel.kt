package app.still.launcher.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.NotesRepository
import app.still.launcher.data.notes.ChecklistItem
import app.still.launcher.data.notes.Note
import app.still.launcher.data.notes.isBlank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NoteDraft(
    val target: EditorTarget.EditNote,
    val original: Note?,
    val title: String,
    val text: String = "",
    val checklist: List<ChecklistLine> = emptyList(),
    val pinned: Boolean = false,
)

class NoteEditorViewModel(private val repository: NotesRepository) : ViewModel() {

    private val _draft = MutableStateFlow<NoteDraft?>(null)

    /** Null while loading. */
    val draft: StateFlow<NoteDraft?> = _draft

    // Saves and loads run one at a time, so reopening right after closing reads the saved version.
    private val mutex = Mutex()
    private var nextKey = 0L
    private var discarded = false

    fun open(target: EditorTarget.EditNote) {
        _draft.value = null
        discarded = false
        viewModelScope.launch {
            mutex.withLock {
                val note = target.noteId?.let { repository.getNote(it) }
                _draft.value = note?.toDraft(target) ?: NoteDraft(target, original = null, title = target.title)
            }
        }
    }

    fun update(change: (NoteDraft) -> NoteDraft) {
        _draft.update { it?.let(change) }
    }

    fun addChecklistItem(text: String) {
        if (text.isBlank()) return
        update { it.copy(checklist = it.checklist + ChecklistLine(nextKey++, text.trim(), done = false)) }
    }

    /**
     * Writes the draft when the editor leaves the screen. An empty note isn't saved; a note
     * whose content didn't change keeps its place in the list (only pinning may have changed).
     */
    fun save() {
        val draft = _draft.value ?: return
        if (discarded) return
        val note = draft.toNote()
        if (note.isBlank) return
        val original = draft.original
        val changed = original == null ||
            note.title != original.title || note.text != original.text || note.checklist != original.checklist
        if (!changed && note.pinned == original.pinned) return
        viewModelScope.launch {
            mutex.withLock { repository.save(if (changed) repository.touched(note) else note) }
        }
    }

    fun delete() {
        discarded = true
        val noteId = _draft.value?.original?.id ?: return
        viewModelScope.launch { mutex.withLock { repository.delete(noteId) } }
    }

    private fun Note.toDraft(target: EditorTarget.EditNote) = NoteDraft(
        target = target,
        original = this,
        title = title,
        text = text,
        checklist = checklist.map { ChecklistLine(nextKey++, it.text, it.done) },
        pinned = pinned,
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                NoteEditorViewModel(container.notesRepository)
            }
        }
    }
}

private fun NoteDraft.toNote(): Note = (original ?: Note()).copy(
    title = title.trim(),
    text = text.trimEnd(),
    checklist = checklist.filter { it.text.isNotBlank() }.map { ChecklistItem(it.text.trim(), it.done) },
    pinned = pinned,
)
