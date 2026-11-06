package app.still.launcher.keynote

import org.junit.Assert.assertEquals
import org.junit.Test

class QualitiesTest {

    @Test
    fun `qualities are tallied by days, most first`() {
        val days = listOf("Patience", "Steadiness", "Steadiness", "Courage", "Steadiness", "Patience")
        assertEquals(listOf("Steadiness" to 3, "Patience" to 2, "Courage" to 1), qualityDays(days))
    }

    private val suggested = listOf("Steadiness", "Patience", "Attention", "Courage", "Goodwill")

    @Test
    fun `a quality of your own is kept with its action, at the end`() {
        val own = withOwnQuality(emptyList(), " Kindness ", " Say one kind thing ", suggested)
        assertEquals(listOf(OwnQuality("Kindness", "Say one kind thing")), own)
        assertEquals(listOf("Kindness", "Calm"), withOwnQuality(own, "Calm", "Breathe first", suggested).map { it.quality })
    }

    @Test
    fun `keeping it again updates its action in place, whatever the case`() {
        val own = listOf(OwnQuality("Kindness", "Say one kind thing"), OwnQuality("Calm", "Breathe first"))
        assertEquals(
            listOf(OwnQuality("kindness", "Help someone"), OwnQuality("Calm", "Breathe first")),
            withOwnQuality(own, "kindness", "Help someone", suggested),
        )
    }

    @Test
    fun `the suggestions and blank names aren't kept`() {
        assertEquals(emptyList<OwnQuality>(), withOwnQuality(emptyList(), "patience", "Wait", suggested))
        assertEquals(emptyList<OwnQuality>(), withOwnQuality(emptyList(), "  ", "Wait", suggested))
    }

    @Test
    fun `one of your own comes off the list, whatever the case`() {
        val own = listOf(OwnQuality("Kindness", "Say one kind thing"), OwnQuality("Calm", "Breathe first"))
        assertEquals(listOf(OwnQuality("Calm", "Breathe first")), withoutOwnQuality(own, "KINDNESS"))
        assertEquals(own, withoutOwnQuality(own, "Courage"))
    }
}
