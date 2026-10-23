package app.still.launcher.data

/** The buttons in home's bottom corners: the phone on the left, the camera on the right. */
enum class CornerButton { PHONE, CAMERA }

/** What a corner button opens: nothing when it's off, else [appKey], or the phone's own app for it when that's null. */
data class CornerChoice(val on: Boolean = true, val appKey: String? = null)

/**
 * The app a corner button opens: the chosen one, or the default when none is chosen or the
 * chosen one has gone; null when the button is off or there's no app for it.
 */
fun <T> cornerApp(choice: CornerChoice, chosen: T?, default: T?): T? = when {
    !choice.on -> null
    choice.appKey != null && chosen != null -> chosen
    else -> default
}
