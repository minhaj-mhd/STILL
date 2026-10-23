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
 * settings needed to undo things: the phone app, Settings, and STILL itself.
 */
class EssentialApps(context: Context) {

    private val appContext = context.applicationContext
    private val telecom = appContext.getSystemService(TelecomManager::class.java)

    private var gateExempt: Set<String> = emptySet()
    private var gateExemptCheckedAt = 0L

    fun isEssential(packageName: String): Boolean =
        packageName == appContext.packageName ||
            packageName == SETTINGS_PACKAGE ||
            packageName == telecom?.defaultDialerPackage

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

    /** [canGate], and not one of the apps the user keeps [alwaysOpen]. */
    fun canGate(packageName: String, alwaysOpen: Set<String>): Boolean = packageName !in alwaysOpen && canGate(packageName)

    private var clock: String? = null
    private var clockCheckedAt = 0L

    @Synchronized
    private fun clockApp(): String? {
        val now = SystemClock.elapsedRealtime()
        if (clockCheckedAt == 0L || now - clockCheckedAt > RECHECK_MILLIS) {
            clock = defaultHandler(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            clockCheckedAt = now
        }
        return clock
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
