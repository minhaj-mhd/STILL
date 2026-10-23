package app.still.launcher.precision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundGateTest {

    private val gate = ForegroundGate(graceMillis = 60_000)
    private val asks = ForegroundRule(asksFirst = true)
    private val blocked = ForegroundRule(blocked = true)
    private val none = ForegroundRule.NONE

    private fun minutes(n: Int) = n * 60_000L

    @Test
    fun `apps without rules are left alone`() {
        assertNull(gate.onForeground("maps", 0, none))
        assertEquals("maps", gate.foreground)
    }

    @Test
    fun `a blocked app is covered on every visit, once per visit`() {
        assertEquals(InterventionKind.BLOCKED, gate.onForeground("insta", 0, blocked))
        assertNull(gate.onForeground("insta", 1_000, blocked))
        assertNull(gate.onForeground("still", 2_000, none))
        assertEquals(InterventionKind.BLOCKED, gate.onForeground("insta", 3_000, blocked))
    }

    @Test
    fun `a block beats a pass`() {
        gate.allow("insta", minutes(10))
        assertEquals(InterventionKind.BLOCKED, gate.onForeground("insta", 0, blocked))
    }

    @Test
    fun `an app that asks first needs a pass`() {
        assertEquals(InterventionKind.ASK, gate.onForeground("insta", 0, asks))
        // Looking again during the same visit doesn't ask twice.
        assertNull(gate.onForeground("insta", 30_000, asks))
    }

    @Test
    fun `opening it from STILL hands out a pass`() {
        gate.allow("insta", 60_000)
        assertNull(gate.onForeground("insta", 1_000, asks))
    }

    @Test
    fun `a pass never runs out while you stay in the app`() {
        gate.allow("insta", 60_000)
        assertNull(gate.onForeground("insta", 1_000, asks))
        assertNull(gate.onForeground("insta", minutes(45), asks))
    }

    @Test
    fun `a quick look at another app doesn't ask again`() {
        gate.allow("insta", 60_000)
        gate.onForeground("insta", 1_000, asks)
        gate.onForeground("whatsapp", minutes(20), none)
        assertNull(gate.onForeground("insta", minutes(20) + 30_000, asks))
    }

    @Test
    fun `coming back after the grace period asks again`() {
        gate.allow("insta", 60_000)
        gate.onForeground("insta", 1_000, asks)
        gate.onForeground("still", minutes(20), none)
        assertEquals(InterventionKind.ASK, gate.onForeground("insta", minutes(22), asks))
    }

    @Test
    fun `a time reminder's pass lasts until the planned time is up`() {
        gate.allow("insta", minutes(10))
        gate.onForeground("insta", 1_000, asks)
        gate.onForeground("still", minutes(1), none)
        assertNull(gate.onForeground("insta", minutes(8), asks))
    }

    @Test
    fun `time's up while in the app counts as stepping in and ends the pass`() {
        gate.allow("insta", minutes(10))
        gate.onForeground("insta", 1_000, asks)
        assertTrue(gate.onTimeUp("insta"))
        // STILL is already stepping in with "Time's up"; the next look doesn't add an ask.
        assertNull(gate.onForeground("insta", minutes(10) + 1_000, asks))
        // Leaving doesn't earn a grace period, so coming straight back asks again.
        gate.onForeground("still", minutes(10) + 2_000, none)
        assertEquals(InterventionKind.ASK, gate.onForeground("insta", minutes(10) + 3_000, asks))
    }

    @Test
    fun `time's up while away ends the pass too`() {
        gate.allow("insta", minutes(10))
        gate.onForeground("insta", 1_000, asks)
        gate.onForeground("still", minutes(9) + 30_000, none)
        assertFalse(gate.onTimeUp("insta"))
        assertEquals(InterventionKind.ASK, gate.onForeground("insta", minutes(10) + 10_000, asks))
    }

    @Test
    fun `a block that starts during a visit is caught when STILL looks again`() {
        gate.allow("insta", 60_000)
        assertNull(gate.onForeground("insta", 1_000, asks))
        assertEquals(InterventionKind.BLOCKED, gate.onForeground("insta", minutes(5), ForegroundRule(blocked = true, asksFirst = true)))
    }

    @Test
    fun `a block spends the pass`() {
        gate.allow("insta", minutes(30))
        assertEquals(InterventionKind.BLOCKED, gate.onForeground("insta", 0, blocked))
        gate.onForeground("still", 1_000, none)
        // The block is over, but the app still has to be opened through STILL's questions.
        assertEquals(InterventionKind.ASK, gate.onForeground("insta", minutes(5), asks))
    }

    @Test
    fun `passes only grow`() {
        gate.allow("insta", minutes(10))
        gate.allow("insta", 60_000)
        assertNull(gate.onForeground("insta", minutes(5), asks))
    }
}
