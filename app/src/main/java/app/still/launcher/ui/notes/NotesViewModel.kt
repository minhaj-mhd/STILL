package app.still.launcher.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.StillApplication
import app.still.launcher.data.NotesRepository
import app.still.launcher.data.notes.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(private val repository: NotesRepository) : ViewModel() {

    /** Pinned first, then most recently changed; null until read once. */
    val notes: StateFlow<List<Note>?> = repository.notes.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setPinned(note: Note, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(note, pinned) }
    }

    fun delete(note: Note) {
        viewModelScope.launch { repository.delete(note.id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                NotesViewModel(container.notesRepository)
            }
        }
    }
}
