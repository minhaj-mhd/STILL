package app.still.launcher.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ActiveBlockTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-23T20:00:00Z")

    private fun scheduled(until: String, name: String = "Evening") =
        mapOf("insta" to ScheduledBlock(LocalDateTime.parse(until), name))

    @Test
    fun `nothing blocks an app that has no block`() {
        assertNull(activeBlock("insta", emptyMap(), emptyMap(), now, zone))
    }

    @Test
    fun `a block set by hand runs until its end`() {
        val until = Instant.parse("2026-10-23T22:00:00Z")
        assertEquals(ActiveBlock(until, null), activeBlock("insta", mapOf("insta" to until), emptyMap(), now, zone))
    }

    @Test
    fun `a block set by hand that has ended doesn't count`() {
        val ended = Instant.parse("2026-10-23T19:00:00Z")
        assertNull(activeBlock("insta", mapOf("insta" to ended), emptyMap(), now, zone))
    }

    @Test
    fun `a schedule blocks until its window ends`() {
        assertEquals(
            ActiveBlock(Instant.parse("2026-10-24T07:00:00Z"), "Evening"),
            activeBlock("insta", emptyMap(), scheduled("2026-10-24T07:00"), now, zone),
        )
    }

    @Test
    fun `when both block an app, the later end counts`() {
        val manual = mapOf("insta" to Instant.parse("2026-10-24T09:00:00Z"))
        assertEquals(ActiveBlock(Instant.parse("2026-10-24T09:00:00Z"), null), activeBlock("insta", manual, scheduled("2026-10-24T07:00"), now, zone))

        val shortManual = mapOf("insta" to Instant.parse("2026-10-23T21:00:00Z"))
        assertEquals("Evening", activeBlock("insta", shortManual, scheduled("2026-10-24T07:00"), now, zone)?.scheduleName)
    }

    @Test
    fun `other apps aren't affected`() {
        assertNull(activeBlock("maps", mapOf("insta" to Instant.parse("2026-10-23T22:00:00Z")), scheduled("2026-10-24T07:00"), now, zone))
    }
}
