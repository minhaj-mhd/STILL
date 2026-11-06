package app.still.launcher.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyReadsTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `a headline drops the date in front of it`() {
        assertEquals("The law of attraction", headlineOf("2026-11-06 — The law of attraction"))
        assertEquals("The law of attraction", headlineOf("2026-11-06: The law of attraction.docx"))
        assertEquals("Thought waves", headlineOf("6 Nov 2026 - Thought waves"))
        assertEquals("Thought waves", headlineOf("Friday, 6 November 2026 | Thought waves"))
        assertEquals("Thought waves", headlineOf("Nov 6, 2026 · Thought waves"))
        assertEquals("Thought waves", headlineOf("Thought waves"))
    }

    @Test
    fun `a headline keeps names that only look like dates`() {
        assertEquals("2026-11-06", headlineOf("2026-11-06"))
        assertEquals("12 Rules for Life", headlineOf("12 Rules for Life"))
        assertEquals("May the force", headlineOf("May the force"))
    }

    @Test
    fun `a series lands at the middle of its recent times`() {
        fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant()
        val arrivals = listOf(at(1, 9, 31), at(2, 9, 29), at(3, 9, 30), at(4, 14, 0), at(5, 9, 32))
        assertEquals(9 * 60 + 31, usualLandingMinute(arrivals, zone))
        assertNull(usualLandingMinute(emptyList(), zone))
    }

    @Test
    fun `checks come after a series is due, then while it's late`() {
        val nineThirty = 9 * 60 + 30
        fun at(hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, 6, hour, minute, 0, 0, zone)
        assertEquals(at(9, 35), nextSeriesCheck(at(7, 0), nineThirty, arrivedToday = false))
        assertEquals(at(10, 20), nextSeriesCheck(at(10, 0), nineThirty, arrivedToday = false))
        assertEquals(at(9, 35).plusDays(1), nextSeriesCheck(at(13, 0), nineThirty, arrivedToday = false))
        assertEquals(at(9, 35).plusDays(1), nextSeriesCheck(at(9, 40), nineThirty, arrivedToday = true))
        assertNull(nextSeriesCheck(at(9, 40), null, arrivedToday = false))
    }

    @Test
    fun `text comes out of a page`() {
        val html = """<html><head><title>T</title><style>p{}</style></head><body><h1>Title</h1><p>Thought is a force &amp; a&nbsp;power.</p></body></html>"""
        assertEquals("Title Thought is a force & a power.", textOfHtml(html))
        assertEquals(6, wordsIn("Thought is a force & a power."))
        assertEquals("Thought is a force & a power.", proseOfHtml(html))
        assertEquals("Title", firstHeadingOf(html))
        assertEquals("Day 23 — The river", firstHeadingOf("""<body><p>x</p><h2 class="a">Day 23 — <em>The river</em></h2><h1>Later</h1></body>"""))
        assertNull(firstHeadingOf("<head><title>T</title></head><body><p>No headings</p></body>"))
    }

    @Test
    fun `reading time goes by words, or by letters where words run long`() {
        assertEquals(1, minutesToRead("a few words"))
        assertEquals(9, minutesToRead(List(2_000) { "force" }.joinToString(" ")))
        // Malayalam words are long: 2,000 of them take about 15 minutes, not 9.
        assertEquals(15, minutesToRead(List(2_000) { "നിങ്ങളുടെ" }.joinToString(" ")))
    }

    @Test
    fun `a name gives the day it starts with`() {
        val saved = LocalDate.of(2026, 11, 6)
        assertEquals(LocalDate.of(2026, 10, 31), dateInName("2026-10-31.docx", saved))
        assertEquals(LocalDate.of(2026, 11, 2), dateInName("2 Nov 2026 - Thought waves", saved))
        assertEquals(LocalDate.of(2026, 11, 4), dateInName("Wednesday, Nov 4 · Thought waves", saved))
        assertEquals(LocalDate.of(2025, 12, 31), dateInName("Dec 31", LocalDate.of(2026, 1, 1)))
        assertNull(dateInName("Thought waves", saved))
        assertNull(dateInName("2026-02-30", saved))
        assertTrue(isOnlyDate("2026-11-06.docx"))
        assertTrue(isOnlyDate("6 November 2026"))
        assertFalse(isOnlyDate("2026-11-06 — Thought waves"))
        assertFalse(isOnlyDate("12 Rules for Life"))
    }

    @Test
    fun `a read saved late keeps the day in its name`() {
        val saved = Instant.parse("2026-11-06T12:40:00Z") // 18:10 in India
        assertEquals(Instant.parse("2026-10-31T12:40:00Z"), arrivalOf("2026-10-31.docx", saved, zone))
        assertEquals(saved, arrivalOf("2026-11-06.docx", saved, zone))
        assertEquals(saved, arrivalOf("2026-11-07.docx", saved, zone))
        assertEquals(saved, arrivalOf("Thought waves.docx", saved, zone))
    }

    @Test
    fun `a week saved at once says nothing about when the chat runs`() {
        val catchUp = (0L..6L).map { back ->
            "${LocalDate.of(2026, 11, 6).minusDays(back)}.docx" to Instant.parse("2026-11-06T12:34:00Z").plusSeconds(back * 300)
        }
        assertEquals(emptyList<Instant>(), scheduledSaves(catchUp, zone))
        assertNull(usualLandingMinute(scheduledSaves(catchUp, zone), zone))
        val tomorrow = "2026-11-07.docx" to Instant.parse("2026-11-07T04:00:00Z") // 9:30 in India
        assertEquals(9 * 60 + 30, usualLandingMinute(scheduledSaves(catchUp + tomorrow, zone), zone))
    }

    @Test
    fun `a read named only by its date takes its heading`() {
        assertEquals("Day 23 — The river", readTitle("2026-11-06", "Day 23 — The river"))
        assertEquals("2026-11-06", readTitle("2026-11-06", null))
        assertEquals("Thought waves", readTitle("Thought waves", "Day 23 — The river"))
    }

    @Test
    fun `an excerpt skips the headline and stops at a word`() {
        assertEquals("Thought is a force.", excerptOf("The law of attraction Thought is a force.", "The law of attraction"))
        val long = "word ".repeat(100).trim()
        val excerpt = excerptOf(long, "", maxChars = 22)!!
        assertEquals("word word word word…", excerpt)
        assertNull(excerptOf("Headline", "Headline"))
    }

    @Test
    fun `the week marks read, waiting and empty days`() {
        val today = LocalDate.of(2026, 11, 6)
        val week = weekOf(mapOf(today.minusDays(1) to listOf(true), today.minusDays(2) to listOf(false)), today)
        assertEquals(7, week.size)
        assertEquals(today, week.last().first)
        assertEquals(WeekDay.TODAY, week.last().second)
        assertEquals(WeekDay.READ, week[5].second)
        assertEquals(WeekDay.WAITING, week[4].second)
        assertEquals(WeekDay.NONE, week[0].second)
        assertEquals(LocalDate.of(2026, 10, 31), keepFrom(today, 7))
    }

    @Test
    fun `an arrival keeps its own day`() {
        val arrived = Instant.parse("2026-11-05T19:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 6), arrived.atZone(zone).toLocalDate())
    }
}
