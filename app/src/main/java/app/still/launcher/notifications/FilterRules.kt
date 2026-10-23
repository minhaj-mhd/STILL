package app.still.launcher.notifications

import android.app.Notification

/** What the filter looks at in a notification as it's posted. */
data class PostedNotification(
    val packageName: String,
    /** Ongoing notifications (music, navigation, calls in progress, downloads) can't be removed by STILL anyway. */
    val isOngoing: Boolean,
    /** Its [Notification.category]; null when the app didn't set one. */
    val category: String?,
    val isGroupSummary: Boolean,
)

enum class FilterDecision {
    /** Leave it in the shade. */
    LET_THROUGH,

    /** Take it out of the shade and keep it in the held-back list. */
    HOLD,

    /** Take it out without keeping it: a group summary, whose messages are held one by one. */
    DISMISS,
}

/** Notification categories that always come through: someone calling, and alarms. */
private val ALWAYS_THROUGH_CATEGORIES = setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM)

/**
 * Whether a notification comes through. Calls, alarms and ongoing notifications always do, as
 * do apps that [alwaysThrough] names (STILL, the phone, clock and system) and the apps the user
 * [allowed]. Everything else is held back.
 */
fun decide(notification: PostedNotification, allowed: Set<String>, alwaysThrough: (String) -> Boolean): FilterDecision = when {
    notification.isOngoing -> FilterDecision.LET_THROUGH
    notification.category in ALWAYS_THROUGH_CATEGORIES -> FilterDecision.LET_THROUGH
    alwaysThrough(notification.packageName) -> FilterDecision.LET_THROUGH
    notification.packageName in allowed -> FilterDecision.LET_THROUGH
    notification.isGroupSummary -> FilterDecision.DISMISS
    else -> FilterDecision.HOLD
}
