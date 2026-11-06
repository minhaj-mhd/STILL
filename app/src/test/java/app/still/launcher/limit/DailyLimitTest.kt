package app.still.launcher.limit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

// 2026-10-23 is a Friday; 24 and 25 are the weekend.
class DailyLimitTest {

    private val friday = LocalDate.parse("2026-10-23")
    private val saturday = LocalDate.parse("2026-10-24")
    private val threeHours = LimitSettings(plan = LimitPlan(180))

    private fun state(usedMinutes: Long, limitMinutes: Int = 180, warn: Int = 15) =
        LimitState(friday, limitMinutes, Duration.ofMinutes(usedMinutes), warn, friday.plusDays(1).atStartOfDay())

    @Test
    fun `weekends can have their own limit`() {
        val plan = LimitPlan(weekdayMinutes = 120, weekendMinutes = 240)
        assertEquals(120, plan.minutesOn(friday))
        assertEquals(240, plan.minutesOn(saturday))
        assertEquals(120, LimitPlan(120).minutesOn(saturday))
    }

    @Test
    fun `a day can start at 4 AM`() {
        assertEquals(friday, limitDay(LocalDateTime.parse("2026-10-24T03:30"), dayStartHour = 4))
        assertEquals(saturday, limitDay(LocalDateTime.parse("2026-10-24T04:00"), dayStartHour = 4))
        assertEquals(saturday, limitDay(LocalDateTime.parse("2026-10-24T00:10"), dayStartHour = 0))
    }

    @Test
    fun `lowering the limit counts straight away`() {
        val lowered = threeHours.withPlan(LimitPlan(120), friday)
        assertEquals(LimitPlan(120), lowered.planOn(friday))
        assertNull(lowered.pending)
    }

    @Test
    fun `a first limit counts straight away`() {
        assertEquals(LimitPlan(120), LimitSettings().withPlan(LimitPlan(120), friday).planOn(friday))
    }

    @Test
    fun `raising the limit waits for tomorrow`() {
        val raised = threeHours.withPlan(LimitPlan(240), friday)
        assertEquals(LimitPlan(180), raised.planOn(friday))
        assertEquals(LimitPlan(240), raised.planOn(saturday))
    }

    @Test
    fun `on the day a limit starts it can be stepped up freely`() {
        val started = LimitSettings().withPlan(LimitPlan(60), friday)
        assertEquals(friday, started.startedOn)
        val stepped = started.withPlan(LimitPlan(75), friday).withPlan(LimitPlan(200), friday)
        assertEquals(LimitPlan(200), stepped.planOn(friday))
        assertNull(stepped.pending)
        // From the next day, raises wait again.
        assertEquals(LimitPlan(200), stepped.withPlan(LimitPlan(300), saturday).planOn(saturday))
        assertEquals(LimitPlan(300), stepped.withPlan(LimitPlan(300), saturday).planOn(saturday.plusDays(1)))
    }

    @Test
    fun `a raise waiting since the start day counts that day`() {
        val waiting = threeHours.copy(pending = PendingPlan(LimitPlan(240), saturday), startedOn = friday)
        assertEquals(LimitPlan(240), waiting.planOn(friday))
    }

    @Test
    fun `switching the limit off is a raise`() {
        val off = threeHours.withPlan(LimitPlan(0), friday)
        assertTrue(off.planOn(friday).isOn)
        assertFalse(off.planOn(saturday).isOn)
    }

    @Test
    fun `a bigger weekend limit is a raise too`() {
        assertTrue(isRaise(LimitPlan(180), LimitPlan(180, weekendMinutes = 300)))
        assertFalse(isRaise(LimitPlan(180), LimitPlan(180, weekendMinutes = 120)))
    }

    @Test
    fun `lowering again replaces a waiting raise`() {
        val settled = threeHours.withPlan(LimitPlan(240), friday).withPlan(LimitPlan(150), friday)
        assertEquals(LimitPlan(150), settled.planOn(friday))
        assertEquals(LimitPlan(150), settled.planOn(saturday))
    }

    @Test
    fun `switching the raise rule off only lets go tomorrow`() {
        val ruleOff = threeHours.withRaiseRule(on = false, today = friday)
        assertTrue(ruleOff.raisesWait(friday))
        assertFalse(ruleOff.raisesWait(saturday))
        // Raising on the day it was switched off still waits.
        assertEquals(LimitPlan(180), ruleOff.withPlan(LimitPlan(300), friday).planOn(friday))
        // From tomorrow, raises count at once.
        assertEquals(LimitPlan(300), ruleOff.withPlan(LimitPlan(300), saturday).planOn(saturday))
    }

    @Test
    fun `without the raise rule, raises count at once`() {
        val free = threeHours.copy(raiseStartsTomorrow = false)
        assertEquals(LimitPlan(300), free.withPlan(LimitPlan(300), friday).planOn(friday))
    }

    @Test
    fun `the state knows what's left, the warning and the end`() {
        assertEquals(Duration.ofMinutes(50), state(130).left)
        assertFalse(state(130).isWarning)
        assertTrue(state(170).isWarning)
        assertTrue(state(180).isReached)
        assertEquals(Duration.ZERO, state(200).left)
        assertFalse(state(170, warn = 0).isWarning)
        assertEquals(1f, state(200).fraction)
    }

    @Test
    fun `after the limit only allowed and always-open apps open`() {
        val settings = threeHours.copy(allowed = setOf("maps"))
        val alwaysOpen = { packageName: String -> packageName == "dialer" }
        val now = 1_000L
        assertTrue(settings.keepsClosed(state(180), "instagram", now, alwaysOpen))
        assertFalse(settings.keepsClosed(state(180), "maps", now, alwaysOpen))
        assertFalse(settings.keepsClosed(state(180), "dialer", now, alwaysOpen))
        assertFalse(settings.keepsClosed(state(100), "instagram", now, alwaysOpen))
        assertFalse(settings.keepsClosed(null, "instagram", now, alwaysOpen))
    }

    @Test
    fun `emergency minutes let one app through for a while, twice a day`() {
        val first = threeHours.withEmergency(friday, "instagram", untilMillis = 5_000)
        assertFalse(first.keepsClosed(state(180), "instagram", 4_000) { false })
        assertTrue(first.keepsClosed(state(180), "instagram", 5_000) { false })
        assertTrue(first.keepsClosed(state(180), "youtube", 4_000) { false })
        assertEquals(1, first.emergenciesLeft(friday))
        assertEquals(0, first.withEmergency(friday, "youtube", 9_000).emergenciesLeft(friday))
        // A new day starts afresh.
        assertEquals(2, first.emergenciesLeft(saturday))
        assertEquals(1, first.withEmergency(saturday, "x", 0).emergency?.used)
    }

    @Test
    fun `usage leaves out STILL, the apps that always open and allowed apps, and says which`() {
        val millis = mapOf("instagram" to 60_000L, "maps" to 120_000L, "dialer" to 600_000L, "still" to 180_000L, "idle" to 0L)
        val usage = LimitSettings(allowed = setOf("maps")).usage(millis, still = "still") { it == "dialer" || it == "still" }
        assertEquals(Duration.ofMinutes(1), usage.counted)
        assertEquals(
            mapOf(Uncounted.ALLOWED to Duration.ofMinutes(2), Uncounted.ALWAYS_OPEN to Duration.ofMinutes(10), Uncounted.STILL to Duration.ofMinutes(3)),
            usage.uncounted,
        )
    }

    @Test
    fun `allowed apps count when the setting says so`() {
        val usage = LimitSettings(allowed = setOf("maps"), countsAllowed = true).usage(mapOf("maps" to 120_000L), still = "still") { false }
        assertEquals(Duration.ofMinutes(2), usage.counted)
        assertEquals(emptyMap<Uncounted, Duration>(), usage.uncounted)
    }
}
