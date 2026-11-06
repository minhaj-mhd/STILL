package app.still.launcher.monochrome

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import app.still.launcher.data.LauncherPreferences
import app.still.launcher.data.LauncherSettings
import app.still.launcher.util.EssentialApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What monochrome needs to know to decide: its settings, focus mode, and the app in front. */
data class MonochromeState(
    val forApps: Boolean,
    val apps: Set<String>,
    val forFocus: Boolean,
    val focusOn: Boolean,
    /** The app in front, or null when Precision mode can't say. */
    val front: String?,
)

/** Grey while one of the chosen apps is in front, or throughout focus mode. */
fun wantsGrey(state: MonochromeState): Boolean =
    (state.forFocus && state.focusOn) || (state.forApps && state.front != null && state.front in state.apps)

/** How Android's colour correction is set: on or off, and which correction. */
data class ColourCorrection(val on: Boolean, val mode: Int) {
    /** Saved in settings until it's put back. */
    fun encode(): String = "${if (on) 1 else 0},$mode"

    val isGrey: Boolean get() = on && mode == MONOCHROMACY

    companion object {
        /** Android's grayscale correction. */
        const val MONOCHROMACY = 0

        /** The correction Android uses when none has been chosen. */
        const val DEFAULT_MODE = 12

        val GREY = ColourCorrection(on = true, mode = MONOCHROMACY)

        fun decode(saved: String): ColourCorrection? {
            val parts = saved.split(',')
            val on = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val mode = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return ColourCorrection(on == 1, mode)
        }
    }
}

/**
 * Monochrome: turns the screen grey through Android's colour correction while chosen apps are
 * open or focus mode is on, and puts the person's own setting back after. Android only lets an
 * app change it once the person has allowed WRITE_SECURE_SETTINGS from a computer; until then
 * this does nothing. Precision mode tells it which app is in front.
 */
class Monochrome(
    context: Context,
    private val preferences: LauncherPreferences,
    private val essentialApps: EssentialApps,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val front = MutableStateFlow<String?>(null)
    private val applying = Mutex()

    /** Whether the person has allowed STILL to change colour correction. */
    val isAllowed: Boolean
        get() = appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    init {
        scope.launch {
            combine(preferences.settings.map { it.monochromeInputs() }.distinctUntilChanged(), front) { inputs, app ->
                wantsGrey(inputs.copy(front = app))
            }.distinctUntilChanged().collect { apply(it) }
        }
    }

    /** [packageName] came to the front. Android's own screens, such as Recents, leave things as they are. */
    fun onFront(packageName: String) {
        if (packageName != appContext.packageName && essentialApps.isSystemScreen(packageName)) return
        front.value = packageName
    }

    /** Precision mode stopped, so which app is in front isn't known any more. */
    fun onPrecisionStopped() {
        front.value = null
    }

    /** Looks again, as after the person has just allowed STILL to change colour correction. */
    fun refresh() {
        scope.launch {
            val settings = preferences.current()
            apply(wantsGrey(settings.monochromeInputs().copy(front = front.value)))
        }
    }

    private suspend fun apply(grey: Boolean) = applying.withLock {
        if (!isAllowed) return@withLock
        val saved = preferences.current().monochromeRestore
        try {
            if (grey) {
                if (saved != null) return@withLock
                val current = read()
                // Already grey by the person's own choice: leave it, and don't put anything back later.
                if (current.isGrey) return@withLock
                preferences.setMonochromeRestore(current.encode())
                write(ColourCorrection.GREY)
            } else {
                saved ?: return@withLock
                write(ColourCorrection.decode(saved) ?: ColourCorrection(on = false, mode = ColourCorrection.DEFAULT_MODE))
                preferences.setMonochromeRestore(null)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Colour correction couldn't be changed", e)
        }
    }

    private fun read(): ColourCorrection = ColourCorrection(
        on = Settings.Secure.getInt(resolver, ENABLED, 0) == 1,
        mode = Settings.Secure.getInt(resolver, MODE, ColourCorrection.DEFAULT_MODE),
    )

    private fun write(correction: ColourCorrection) {
        Settings.Secure.putInt(resolver, MODE, correction.mode)
        Settings.Secure.putInt(resolver, ENABLED, if (correction.on) 1 else 0)
    }

    private fun LauncherSettings.monochromeInputs() = MonochromeState(
        forApps = monochromeForApps,
        apps = monochromeApps,
        forFocus = monochromeForFocus,
        focusOn = focusModeSince != null,
        front = null,
    )

    companion object {
        /** What to run from a computer, once, so STILL may change colour correction. */
        fun grantCommand(packageName: String) = "adb shell pm grant $packageName ${Manifest.permission.WRITE_SECURE_SETTINGS}"

        private const val TAG = "StillMonochrome"
        private const val ENABLED = "accessibility_display_daltonizer_enabled"
        private const val MODE = "accessibility_display_daltonizer"
    }
}
