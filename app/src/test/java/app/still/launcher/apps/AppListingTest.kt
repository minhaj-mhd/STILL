package app.still.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListingTest {

    @Test
    fun `sections start where each first letter does`() {
        val labels = listOf("Calendar", "Camera", "Chrome", "Maps", "Messages", "YouTube")
        assertEquals(
            listOf(ListSection("C", 0), ListSection("M", 3), ListSection("Y", 5)),
            listSections(labels),
        )
    }

    @Test
    fun `accents and case share a letter`() {
        val labels = listOf("eBay", "Écran", "Email")
        assertEquals(listOf(ListSection("E", 0)), listSections(labels))
    }

    @Test
    fun `digits and symbols share the other section`() {
        val labels = listOf("1Password", "7-Zip", "@Home", "Agenda")
        assertEquals(listOf(ListSection(OTHER_SECTION, 0), ListSection("A", 3)), listSections(labels))
    }

    @Test
    fun `a letter that comes back later keeps its first place`() {
        val labels = listOf("Alpha", "Beta", "Ápex")
        assertEquals(listOf(ListSection("A", 0), ListSection("B", 1)), listSections(labels))
    }

    @Test
    fun `blank names go under the other section`() {
        assertEquals(OTHER_SECTION, sectionLetter("  "))
    }

    @Test
    fun `recently installed keeps the last few days, newest first`() {
        val now = 10 * DAY
        val apps = listOf("old" to now - 4 * DAY, "today" to now - HOUR, "yesterday" to now - DAY, "edge" to now - 3 * DAY)
        assertEquals(listOf("today", "yesterday"), recentlyInstalled(apps, now) { it.second }.map { it.first })
    }

    @Test
    fun `recently installed shows at most five`() {
        val now = 10 * DAY
        val apps = (1..8).map { "app$it" to now - it * HOUR }
        assertEquals((1..RECENT_MAX).map { "app$it" }, recentlyInstalled(apps, now) { it.second }.map { it.first })
    }

    @Test
    fun `apps fade only after ten minutes, down to the floor at two hours`() {
        assertEquals(1f, fadeForUse(0), 0f)
        assertEquals(1f, fadeForUse(FADE_FROM_MINUTES), 0f)
        assertEquals(0.65f, fadeForUse(65), 0.001f)
        assertEquals(FADE_FLOOR, fadeForUse(FADE_FULL_MINUTES), 0f)
        assertEquals(FADE_FLOOR, fadeForUse(600), 0f)
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
    }
}
