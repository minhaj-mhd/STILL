package app.still.launcher.focusmode

import app.still.launcher.data.CornerChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusModeTest {

    @Test
    fun `an app key belongs to the package before the slash`() {
        assertEquals("com.whatsapp", packageOfKey("com.whatsapp/.Main#0"))
        assertEquals("com.x", packageOfKey("com.x/com.x.Main#10"))
    }

    @Test
    fun `home apps, corner buttons and the always-open apps make the allowlist`() {
        val allowed = focusAllowlist(
            homeKeys = listOf("com.whatsapp/.Main#0", "com.google.android.gm/.Gmail#0", "com.whatsapp/.Main#10"),
            cornerPackages = listOf("com.google.android.dialer", null),
            alwaysOpen = listOf("app.still.launcher", "com.google.android.deskclock", null),
        )
        assertEquals(
            setOf("com.whatsapp", "com.google.android.gm", "com.google.android.dialer", "app.still.launcher", "com.google.android.deskclock"),
            allowed,
        )
    }

    @Test
    fun `a corner button opens its chosen app, the default, or nothing when off`() {
        assertEquals("org.camera", cornerPackage(CornerChoice(appKey = "org.camera/.Cam#0"), defaultPackage = "com.google.camera"))
        assertEquals("com.google.camera", cornerPackage(CornerChoice(), defaultPackage = "com.google.camera"))
        assertNull(cornerPackage(CornerChoice(on = false, appKey = "org.camera/.Cam#0"), defaultPackage = "com.google.camera"))
    }

    @Test
    fun `starting takes a short hold and ending a long one`() {
        assertEquals(START_HOLD_MILLIS, holdMillisFor(focusOn = false))
        assertEquals(30_000L, holdMillisFor(focusOn = true))
    }
}
