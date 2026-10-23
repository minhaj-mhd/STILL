package app.still.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FoldersTest {

    private val folders = listOf(
        AppFolder("work", "Work", listOf("gmail", "slack")),
        AppFolder("places", "Places", listOf("uber")),
    )

    @Test
    fun `moving an app takes it out of its old folder and adds it at the end`() {
        val moved = moveToFolder(folders, "slack", "places")
        assertEquals(listOf("gmail"), moved[0].apps)
        assertEquals(listOf("uber", "slack"), moved[1].apps)
    }

    @Test
    fun `an app is in one folder at most`() {
        val moved = moveToFolder(moveToFolder(folders, "maps", "work"), "maps", "places")
        assertEquals(listOf("gmail", "slack"), moved[0].apps)
        assertEquals(listOf("uber", "maps"), moved[1].apps)
    }

    @Test
    fun `moving to no folder takes it out`() {
        val moved = moveToFolder(folders, "uber", null)
        assertEquals(emptyList<String>(), moved[1].apps)
        assertEquals(listOf("gmail", "slack"), moved[0].apps)
    }
}
