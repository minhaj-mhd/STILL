package app.still.launcher.monochrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonochromeTest {

    private val off = MonochromeState(forApps = true, apps = setOf("com.instagram.android"), forFocus = true, focusOn = false, front = null)

    @Test
    fun `grey while a chosen app is in front`() {
        assertTrue(wantsGrey(off.copy(front = "com.instagram.android")))
        assertFalse(wantsGrey(off.copy(front = "com.whatsapp")))
        assertFalse(wantsGrey(off))
    }

    @Test
    fun `grey throughout focus mode, whatever is in front`() {
        assertTrue(wantsGrey(off.copy(focusOn = true)))
        assertTrue(wantsGrey(off.copy(focusOn = true, front = "com.whatsapp")))
    }

    @Test
    fun `each trigger only counts when its switch is on`() {
        assertFalse(wantsGrey(off.copy(forApps = false, front = "com.instagram.android")))
        assertFalse(wantsGrey(off.copy(forFocus = false, focusOn = true)))
    }

    @Test
    fun `the person's colour correction is saved and read back`() {
        val theirs = ColourCorrection(on = true, mode = 12)
        assertEquals(theirs, ColourCorrection.decode(theirs.encode()))
        assertEquals(ColourCorrection(on = false, mode = 0), ColourCorrection.decode("0,0"))
        assertNull(ColourCorrection.decode(""))
        assertNull(ColourCorrection.decode("on"))
    }

    @Test
    fun `only grayscale correction counts as grey`() {
        assertTrue(ColourCorrection.GREY.isGrey)
        assertFalse(ColourCorrection(on = true, mode = 12).isGrey)
        assertFalse(ColourCorrection(on = false, mode = ColourCorrection.MONOCHROMACY).isGrey)
    }
}
