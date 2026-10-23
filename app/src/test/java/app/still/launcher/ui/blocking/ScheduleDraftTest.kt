package app.still.launcher.ui.blocking

import app.still.launcher.ui.editor.EditorTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDraftTest {

    private val custom = ScheduleDraft(
        target = EditorTarget.EditSchedule(null),
        original = null,
        name = "My schedule",
        startMinute = 21 * 60,
        endMinute = 7 * 60,
    )

    @Test
    fun `a schedule without apps isn't complete`() {
        assertFalse(custom.isComplete)
    }

    @Test
    fun `apps make it complete, even with a blank name`() {
        assertTrue(custom.copy(packages = setOf("com.instagram.android")).isComplete)
        assertTrue(custom.copy(name = "  ", packages = setOf("com.instagram.android")).isComplete)
    }
}
