package app.still.launcher.keynote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekReflectionTest {

    @Test
    fun `turning back is counted against every pause`() {
        assertEquals(2 to 3, turnedBackOf(listOf(true, false, true)))
        assertEquals(0 to 0, turnedBackOf(emptyList()))
    }

    @Test
    fun `qualities are tallied by days, most first`() {
        val days = listOf("Patience", "Steadiness", "Steadiness", "Courage", "Steadiness", "Patience")
        assertEquals(listOf("Steadiness" to 3, "Patience" to 2, "Courage" to 1), qualityDays(days))
    }

    @Test
    fun `unlocks a day average the days that have any`() {
        assertEquals(20, perDay(listOf(18, 22, 0, 20)))
        assertNull(perDay(listOf(0, 30, 0, 12)))
    }

    @Test
    fun `a week with nothing in it is empty`() {
        assertTrue(WeekReflection().isEmpty)
    }
}
