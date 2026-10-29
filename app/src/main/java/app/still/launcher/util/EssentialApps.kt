package app.still.launcher.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Apps STILL never blocks, so a block can't lock you out of calling for help or of the
 * settings needed to undo things: the phone app, Settings, and STILL itself. Nor does it close
 * Android's own screens that other apps open, such as "Open with", permission prompts and the
 * file picker, or Recents.
 */
class EssentialApps(context: Context) {

    private val appContext = context.applicationContext
    private val telecom = appContext.getSystemService(TelecomManager::class.java)

    private var gateExempt: Set<String> = emptySet()
    private var gateExemptCheckedAt = 0L

    fun isEssential(packageName: String): Boolean =
        packageName == appContext.packageName ||
            packageName == SETTINGS_PACKAGE ||
            packageName == telecom?.defaultDialerPackage ||
            isSystemScreen(packageName)

    /**
     * Android's own screens: what other apps open to choose an app, share, ask for a permission,
     * install or pick a file, the call screens, and launchers' Recents. Closing one would close
     * part of the app that opened it, e.g. a PDF from an allowed app.
     */
    fun isSystemScreen(packageName: String): Boolean = packageName in SYSTEM_SCREENS || packageName in launchers

    /** Launchers, whose Recents and home animations show between apps. */
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

    /**
     * Whether a task gate may close this app. Gates never close the essential apps, nor the
     * default messages, contacts, clock, camera and maps apps: you may need those before your
     * tasks are done.
     */
    fun canGate(packageName: String): Boolean = !isEssential(packageName) && packageName !in gateExemptApps()

    /**
     * Apps whose notifications always come through the notification filter: the essential ones,
     * the system, and the clock app, so alarms can't be held back.
     */
    fun notificationsAlwaysThrough(packageName: String): Boolean =
        isEssential(packageName) || packageName in SYSTEM_PACKAGES || packageName == clockApp()

    /** Apps that open even when the daily phone limit is used up, and whose time never counts: the essential ones and the clock. */
    fun alwaysOpenAfterLimit(packageName: String): Boolean = isEssential(packageName) || packageName == clockApp()

    /** [canGate], and not one of the apps the user keeps [alwaysOpen]. */
    fun canGate(packageName: String, alwaysOpen: Set<String>): Boolean = packageName !in alwaysOpen && canGate(packageName)

    /** The phone's own phone app, which home's phone button opens unless another is chosen. */
    fun phoneApp(): String? = telecom?.defaultDialerPackage

    /** The phone's own camera app, which home's camera button opens unless another is chosen. */
    fun cameraApp(): String? = cachedHandler(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)

    /** The phone's own clock app, which focus mode keeps open so alarms can be dismissed. */
    fun clockApp(): String? = cachedHandler(AlarmClock.ACTION_SHOW_ALARMS)

    /** When each default handler was looked up, and what it was. */
    private val handlers = HashMap<String, Pair<Long, String?>>()

    /** [defaultHandler] for [action], looked up again after [RECHECK_MILLIS]. */
    @Synchronized
    private fun cachedHandler(action: String): String? {
        val now = SystemClock.elapsedRealtime()
        handlers[action]?.let { (checkedAt, packageName) -> if (now - checkedAt <= RECHECK_MILLIS) return packageName }
        return defaultHandler(Intent(action)).also { handlers[action] = now to it }
    }

    /** The defaults can change, so they're looked up again now and then rather than once. */
    @Synchronized
    private fun gateExemptApps(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (gateExemptCheckedAt == 0L || now - gateExemptCheckedAt > RECHECK_MILLIS) {
            gateExempt = buildSet {
                Telephony.Sms.getDefaultSmsPackage(appContext)?.let(::add)
                GATE_EXEMPT_INTENTS.mapNotNullTo(this) { defaultHandler(it()) }
            }
            gateExemptCheckedAt = now
        }
        return gateExempt
    }

    /** The app that opens [intent]; null when there's none, or when Android would ask which one. */
    private fun defaultHandler(intent: Intent): String? {
        val packageManager = appContext.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, 0)
        }
        return resolved?.activityInfo?.packageName?.takeIf { it != SYSTEM_CHOOSER_PACKAGE }
    }

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"

        /** Android itself and the system UI post notifications that must stay, e.g. about charging or updates. */
        val SYSTEM_PACKAGES = setOf("android", "com.android.systemui")

        /** The "which app?" chooser comes from the system package. */
        const val SYSTEM_CHOOSER_PACKAGE = "android"

        /** See [isSystemScreen]; the Google names are Pixel's builds of the same screens. */
        val SYSTEM_SCREENS = setOf(
            "android",
            "com.android.systemui",
            "com.android.intentresolver",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.android.credentialmanager",
        )

        const val RECHECK_MILLIS = 60_000L

        val GATE_EXEMPT_INTENTS: List<() -> Intent> = listOf(
            { Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING) },
            { Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS) },
            { Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS) },
            { Intent(AlarmClock.ACTION_SHOW_ALARMS) },
            { Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA) },
        )
    }
}
