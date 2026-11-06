package app.still.launcher.keynote

import org.junit.Assert.assertEquals
import org.junit.Test

class QualitiesTest {

    @Test
    fun `qualities are tallied by days, most first`() {
        val days = listOf("Patience", "Steadiness", "Steadiness", "Courage", "Steadiness", "Patience")
        assertEquals(listOf("Steadiness" to 3, "Patience" to 2, "Courage" to 1), qualityDays(days))
    }
}
