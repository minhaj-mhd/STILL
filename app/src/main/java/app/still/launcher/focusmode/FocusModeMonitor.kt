package app.still.launcher.focusmode

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.LauncherSettings
import app.still.launcher.util.EssentialApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Focus mode: while it's on, only the apps reachable from home open, plus STILL, the phone and
 * clock apps, and the system screens other apps need (permissions, the in-call screen). It's
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

    /** Launchers, whose Recents and home animations focus mode leaves alone; apps opened from Recents are still locked. */
    private val launchers: Set<String> by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val packageManager = appContext.packageManager
        val handlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        handlers.mapTo(HashSet()) { it.activityInfo.packageName }
    }

    val isOn: Boolean get() = settings.value.focusModeSince != null

    /** Whether focus mode keeps [packageName] closed right now. */
    fun locks(packageName: String): Boolean = isOn && packageName !in allowed()

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
            alwaysOpen = listOf(appContext.packageName, essentialApps.phoneApp(), essentialApps.clockApp()) + SYSTEM_PACKAGES + launchers,
        )
    }

    suspend fun start() = preferences.setFocusModeSince(System.currentTimeMillis())

    suspend fun end() = preferences.setFocusModeSince(null)

    private companion object {
        /** Android itself, the system UI, calls and the permission screens apps on home may need. */
        val SYSTEM_PACKAGES = listOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )
    }
}
