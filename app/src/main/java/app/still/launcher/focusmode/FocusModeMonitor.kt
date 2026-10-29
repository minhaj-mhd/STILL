package app.still.launcher.focusmode

import android.content.Context
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.LauncherSettings
import app.still.launcher.util.EssentialApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Focus mode: while it's on, only the apps reachable from home open, plus STILL, the phone and
 * clock apps, and Android's own screens other apps need (see [EssentialApps.isSystemScreen]). It's
 * saved, so it stays on through a restart until the ring is held to end it.
 */
class FocusModeMonitor(
    context: Context,
    private val preferences: LauncherPreferences,
    private val essentialApps: EssentialApps,
    scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val settings: StateFlow<LauncherSettings> = preferences.settings.stateIn(scope, SharingStarted.Eagerly, LauncherSettings())

    val isOn: Boolean get() = settings.value.focusModeSince != null

    /** Whether focus mode keeps [packageName] closed right now. */
    fun locks(packageName: String): Boolean = isOn && packageName !in allowed() && !essentialApps.isSystemScreen(packageName)

    /** What focus mode lets open, worked out from home as it is now. */
    fun allowed(): Set<String> {
        val current = settings.value
        val homeKeys = (current.favorites + current.folders.flatMap { it.apps }).filterNot { it in current.hidden }
        return focusAllowlist(
            homeKeys = homeKeys,
            cornerPackages = listOf(
                cornerPackage(current.phoneButton, essentialApps.phoneApp()),
                cornerPackage(current.cameraButton, essentialApps.cameraApp()),
            ),
            alwaysOpen = listOf(appContext.packageName, essentialApps.phoneApp(), essentialApps.clockApp()),
        )
    }

    suspend fun start() = preferences.setFocusModeSince(System.currentTimeMillis())

    suspend fun end() = preferences.setFocusModeSince(null)
}
