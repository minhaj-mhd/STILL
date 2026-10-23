package app.still.launcher.data.notes

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ChecklistItem(val text: String, val done: Boolean = false)

/** A note: free text, an optional checklist, or both. Pinned notes sort first. */
@Entity(tableName = "notes", indices = [Index("pinned", "updatedAt")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val text: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Instant = Instant.now(),
    /** Last time the content changed; opening a note without editing it doesn't move it. */
    val updatedAt: Instant = Instant.now(),
)

// Derived values live outside the entity so Room doesn't treat them as columns.

/** Nothing written at all; such a note isn't worth saving. */
val Note.isBlank: Boolean
    get() = title.isBlank() && text.isBlank() && checklist.none { it.text.isNotBlank() }

/** The title, or else the first line of text, or else the first checklist item. Null if all are empty. */
val Note.displayTitle: String?
    get() = title.trim().ifEmpty { null }
        ?: text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        ?: checklist.map { it.text.trim() }.firstOrNull { it.isNotEmpty() }

/** A line of text after the title, for the list; null when there's nothing more to show. */
val Note.previewLine: String?
    get() {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        // When the title came from the text's first line, preview the line after it.
        return if (title.isBlank()) lines.drop(1).firstOrNull() else lines.firstOrNull()
    }

val Note.checklistDone: Int get() = checklist.count { it.done }

/** Pinned notes first, then the most recently changed. */
fun List<Note>.sortedForList(): List<Note> =
    sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
