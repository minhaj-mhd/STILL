package app.still.launcher.blocking

import app.still.launcher.blocking.AppUsageEvent.Kind.BACKGROUND
import app.still.launcher.blocking.AppUsageEvent.Kind.FOREGROUND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Chrome Remote Desktop is a web app: it opens, then shows itself in a Chrome tab in its own task.
class TabOwnersTest {

    private val minute = 60_000L
    private val still = "app.still.launcher"
    private val recents = "com.google.android.apps.nexuslauncher"
    private val chrome = "com.android.chrome"
    private val remoteDesktop = "com.google.chromeremotedesktop"
    private val gmail = "com.google.android.gm"

    private val tab = "org.chromium.chrome.browser.customtabs.CustomTabActivity"
    private val browser = "org.chromium.chrome.browser.ChromeTabbedActivity"
    private val doorway = "org.chromium.chrome.browser.document.ChromeLauncherActivity"
    private val remoteDesktopStart = "com.google.remoting.androidwrapper.MainActivity"

    private fun owners() = TabOwners { it != still && it != recents }

    @Test
    fun `a tab counts as the app that opened it`() {
        val owners = owners()
        assertEquals(remoteDesktop, owners.onFront(remoteDesktop, remoteDesktopStart))
        assertEquals(remoteDesktop, owners.onFront(chrome, tab))
    }

    @Test
    fun `Chrome's doorway on the way to the tab counts as the app too`() {
        // What the Pixel showed on 28 October: Remote Desktop, then Chrome's doorway, then the tab.
        val owners = owners()
        owners.opening(remoteDesktop, 0)
        assertEquals(remoteDesktop, owners.onFront(remoteDesktop, remoteDesktopStart))
        assertEquals(remoteDesktop, owners.onFront(chrome, doorway))
        assertEquals(remoteDesktop, owners.onFront(chrome, tab))
    }

    @Test
    fun `opening Chrome through its doorway is Chrome`() {
        val owners = owners()
        owners.onFront(remoteDesktop, remoteDesktopStart)
        owners.onFront(chrome, tab)
        owners.onFront(still, "app.still.launcher.MainActivity")
        owners.opening(chrome, 0)
        assertEquals(chrome, owners.onFront(chrome, doorway))
        assertEquals(chrome, owners.onFront(chrome, browser))
    }

    @Test
    fun `a link another app opens in Chrome is Chrome once Chrome's window shows`() {
        val owners = owners()
        owners.onFront(gmail, "com.google.android.gm.ConversationListActivityGmail")
        assertEquals(gmail, owners.onFront(chrome, doorway))
        assertEquals(chrome, owners.onFront(chrome, browser))
    }

    @Test
    fun `Chrome itself is still Chrome`() {
        val owners = owners()
        owners.onFront(remoteDesktop, remoteDesktopStart)
        owners.onFront(chrome, tab)
        owners.onFront(still, "app.still.launcher.MainActivity")
        assertEquals(chrome, owners.onFront(chrome, browser))
    }

    @Test
    fun `coming back to a tab from Recents keeps its app`() {
        val owners = owners()
        owners.onFront(remoteDesktop, remoteDesktopStart)
        owners.onFront(chrome, tab)
        owners.onFront(recents, "com.android.quickstep.RecentsActivity")
        assertEquals(remoteDesktop, owners.onFront(chrome, tab))
    }

    @Test
    fun `a tab STILL brings back by opening its app counts as that app`() {
        val owners = owners()
        owners.onFront(gmail, "com.google.android.gm.ConversationListActivityGmail")
        owners.onFront(chrome, tab)
        owners.onFront(still, "app.still.launcher.MainActivity")
        // Remote Desktop's task is still open, so only its tab comes back.
        owners.opening(remoteDesktop, 0)
        assertEquals(remoteDesktop, owners.onFront(chrome, tab))
    }

    @Test
    fun `a tab nobody is known to have opened counts as the browser`() {
        assertEquals(chrome, owners().onFront(chrome, tab))
    }

    @Test
    fun `only browsers' own tab windows count as tabs`() {
        assertTrue(TabOwners.isBrowserTab(tab))
        assertTrue(TabOwners.isBrowserTab("org.chromium.chrome.browser.webapps.SameTaskWebApkActivity"))
        assertTrue(TabOwners.isBrowserTab("org.mozilla.fenix.customtabs.ExternalAppBrowserActivity"))
        assertTrue(TabOwners.isBrowserTab(doorway))
        assertFalse(TabOwners.isBrowserTab(browser))
        assertFalse(TabOwners.isBrowserTab("com.facebook.CustomTabActivity"))
        assertFalse(TabOwners.isBrowserTab(null))
    }

    @Test
    fun `screen time in a tab counts for its app, not the browser`() {
        val events = listOf(
            AppUsageEvent(still, 0, FOREGROUND, "app.still.launcher.MainActivity"),
            AppUsageEvent(still, 1 * minute, BACKGROUND, "app.still.launcher.MainActivity"),
            AppUsageEvent(remoteDesktop, 1 * minute, FOREGROUND, remoteDesktopStart),
            AppUsageEvent(remoteDesktop, 2 * minute, BACKGROUND, remoteDesktopStart),
            AppUsageEvent(chrome, 2 * minute, FOREGROUND, doorway),
            AppUsageEvent(chrome, 2 * minute, BACKGROUND, doorway),
            AppUsageEvent(chrome, 2 * minute, FOREGROUND, tab),
            AppUsageEvent(chrome, 32 * minute, BACKGROUND, tab),
            AppUsageEvent(chrome, 32 * minute, FOREGROUND, browser),
            AppUsageEvent(chrome, 40 * minute, BACKGROUND, browser),
        )
        val byApp = foregroundTimeByApp(owners().attribute(events), 0, 60 * minute)
        assertEquals(31 * minute, byApp[remoteDesktop])
        assertEquals(8 * minute, byApp[chrome])
    }

    @Test
    fun `screen time follows the apps STILL opened`() {
        val owners = owners()
        owners.opening(remoteDesktop, 5 * minute)
        val events = listOf(
            AppUsageEvent(still, 0, FOREGROUND, "app.still.launcher.MainActivity"),
            AppUsageEvent(still, 5 * minute, BACKGROUND, "app.still.launcher.MainActivity"),
            AppUsageEvent(chrome, 5 * minute, FOREGROUND, tab),
            AppUsageEvent(chrome, 15 * minute, BACKGROUND, tab),
        )
        assertEquals(mapOf(still to 5 * minute, remoteDesktop to 10 * minute), foregroundTimeByApp(owners.attribute(events), 0, 60 * minute))
    }
}
