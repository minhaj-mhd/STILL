package app.still.launcher.data.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NoteTest {

    @Test
    fun `a note with only whitespace or empty checklist items is blank`() {
        assertTrue(Note(title = "  ", text = "\n ", checklist = listOf(ChecklistItem(" "))).isBlank)
        assertFalse(Note(checklist = listOf(ChecklistItem("Milk"))).isBlank)
    }

    @Test
    fun `title falls back to the first line, then the first checklist item`() {
        assertEquals("Groceries", Note(title = " Groceries ", text = "Milk").displayTitle)
        assertEquals("Ideas", Note(text = "\n  Ideas\nMore").displayTitle)
        assertEquals("Milk", Note(checklist = listOf(ChecklistItem(""), ChecklistItem("Milk"))).displayTitle)
        assertNull(Note().displayTitle)
    }

    @Test
    fun `preview skips the line already used as the title`() {
        assertEquals("More", Note(text = "Ideas\n\nMore").previewLine)
        assertEquals("Ideas", Note(title = "Week", text = "Ideas\nMore").previewLine)
        assertNull(Note(text = "Only one line").previewLine)
    }

    @Test
    fun `pinned notes come first, then the most recently changed`() {
        val old = Note(id = 1, updatedAt = Instant.parse("2026-10-01T00:00:00Z"))
        val recent = Note(id = 2, updatedAt = Instant.parse("2026-10-20T00:00:00Z"))
        val pinnedOld = Note(id = 3, pinned = true, updatedAt = Instant.parse("2026-09-01T00:00:00Z"))
        assertEquals(listOf(3L, 2L, 1L), listOf(old, recent, pinnedOld).sortedForList().map { it.id })
    }

    @Test
    fun `checklist progress counts ticked items`() {
        assertEquals(1, Note(checklist = listOf(ChecklistItem("a", done = true), ChecklistItem("b"))).checklistDone)
    }
}
