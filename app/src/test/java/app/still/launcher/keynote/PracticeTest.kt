package app.still.launcher.keynote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeTest {

    @Test
    fun `marks come in time order, whole-day ones last`() {
        val moments = practiceMoments(
            turnedBack = listOf("insta" to 3 * HOUR),
            practised = Completion("Report", 1 * HOUR),
            focused = listOf(Completion("Read", 2 * HOUR, minutes = 25)),
            withinLimits = listOf("youtube" to 30),
        )
        assertEquals(
            listOf(MomentKind.PRACTISED, MomentKind.FOCUSED, MomentKind.TURNED_BACK, MomentKind.WITHIN_LIMIT),
            moments.map { it.kind },
        )
        assertNull(moments.last().atMillis)
        assertEquals(25, moments[1].minutes)
    }

    @Test
    fun `a burst of turning back from one app counts once`() {
        val tries = listOf("insta" to 0L, "insta" to 2 * MINUTE, "insta" to 6 * MINUTE, "youtube" to 3 * MINUTE)
        val moments = practiceMoments(tries, practised = null, focused = emptyList(), withinLimits = emptyList())
        assertEquals(listOf("insta" to 0L, "youtube" to 3 * MINUTE), moments.map { it.subject to it.atMillis })
    }

    @Test
    fun `tries further apart each count`() {
        val tries = listOf("insta" to 0L, "insta" to 10 * MINUTE)
        assertEquals(2, practiceMoments(tries, null, emptyList(), emptyList()).size)
    }

    @Test
    fun `unlocks by now count each earlier day up to the same time`() {
        val days = listOf(listOf(1L, 5L, 9L), listOf(2L, 20L))
        assertEquals(listOf(2, 1), unlocksByNow(days, offsetMillis = 6L))
    }

    @Test
    fun `unlocks compare with the median of earlier days`() {
        val past = listOf(20, 24, 26, 30, 40)
        assertEquals(UnlockTrend.FEWER, unlockObservation(18, past).trend)
        assertEquals(UnlockTrend.ABOUT, unlockObservation(27, past).trend)
        assertEquals(UnlockTrend.MORE, unlockObservation(31, past).trend)
    }

    @Test
    fun `small counts need a difference of more than two`() {
        assertEquals(UnlockTrend.ABOUT, unlockObservation(2, listOf(4, 4, 4)).trend)
        assertEquals(UnlockTrend.FEWER, unlockObservation(1, listOf(4, 4, 4)).trend)
    }

    @Test
    fun `no trend without a few earlier days`() {
        val observation = unlockObservation(12, listOf(10, 30))
        assertEquals(12, observation.count)
        assertNull(observation.trend)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
