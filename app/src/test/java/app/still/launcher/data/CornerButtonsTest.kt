package app.still.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CornerButtonsTest {

    @Test
    fun `a button that's off opens nothing`() {
        assertNull(cornerApp(CornerChoice(on = false, appKey = "camera2"), chosen = "camera2", default = "camera"))
    }

    @Test
    fun `with no app chosen it opens the default`() {
        assertEquals("camera", cornerApp(CornerChoice(), chosen = null, default = "camera"))
    }

    @Test
    fun `a chosen app wins over the default`() {
        assertEquals("camera2", cornerApp(CornerChoice(appKey = "camera2"), chosen = "camera2", default = "camera"))
    }

    @Test
    fun `a chosen app that was uninstalled falls back to the default`() {
        assertEquals("camera", cornerApp(CornerChoice(appKey = "camera2"), chosen = null, default = "camera"))
    }

    @Test
    fun `no default and nothing chosen means no button`() {
        assertNull(cornerApp(CornerChoice(), chosen = null, default = null))
    }
}
