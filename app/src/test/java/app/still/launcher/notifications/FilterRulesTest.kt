package app.still.launcher.notifications

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterRulesTest {

    private val allowed = setOf("whatsapp")
    private val alwaysThrough = { packageName: String -> packageName == "dialer" || packageName == "clock" }

    private fun posted(
        packageName: String = "instagram",
        ongoing: Boolean = false,
        category: String? = null,
        summary: Boolean = false,
    ) = PostedNotification(packageName, ongoing, category, summary)

    private fun decideFor(notification: PostedNotification) = decide(notification, allowed, alwaysThrough)

    @Test
    fun `notifications from other apps are held back`() {
        assertEquals(FilterDecision.HOLD, decideFor(posted()))
    }

    @Test
    fun `allowed apps come through`() {
        assertEquals(FilterDecision.LET_THROUGH, decideFor(posted("whatsapp")))
    }

    @Test
    fun `the phone, clock and STILL always come through`() {
        assertEquals(FilterDecision.LET_THROUGH, decideFor(posted("dialer")))
        assertEquals(FilterDecision.LET_THROUGH, decideFor(posted("clock")))
    }

    @Test
    fun `calls and alarms come through from any app`() {
        assertEquals(FilterDecision.LET_THROUGH, decideFor(posted("telegram", category = Notification.CATEGORY_CALL)))
        assertEquals(FilterDecision.LET_THROUGH, decideFor(posted("sleep-app", category = Notification.CATEGORY_ALARM)))
        assertEquals(FilterDecision.HOLD, decideFor(posted("telegram", category = Notification.CATEGORY_MESSAGE)))
    }

    @Test
    fun `ongoing notifications come through`() {
        assertEquals(FilterDecision.LET_THROUGH, decideFor(posted("spotify", ongoing = true)))
    }

    @Test
    fun `a group summary is removed without being kept`() {
        assertEquals(FilterDecision.DISMISS, decideFor(posted(summary = true)))
        assertEquals(FilterDecision.LET_THROUGH, decideFor(posted("whatsapp", summary = true)))
    }
}
