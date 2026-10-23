package app.still.launcher.data

import app.still.launcher.data.notes.Note
import app.still.launcher.data.notes.NoteDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class NotesRepository(private val dao: NoteDao) {

    /** Pinned first, then most recently changed. */
    val notes: Flow<List<Note>> = dao.observeNotes()

    suspend fun getNote(noteId: Long): Note? = dao.getNote(noteId)

    /** Saves a new or edited note; returns its id. */
    suspend fun save(note: Note): Long =
        if (note.id == 0L) dao.insert(note) else note.id.also { dao.update(note) }

    /** Pinning isn't an edit, so it leaves [Note.updatedAt] alone. */
    suspend fun setPinned(note: Note, pinned: Boolean) = dao.update(note.copy(pinned = pinned))

    suspend fun delete(noteId: Long) = dao.delete(noteId)

    /** A copy of [note] marked as changed now. */
    fun touched(note: Note): Note = note.copy(updatedAt = Instant.now())
}
