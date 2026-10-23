package app.still.launcher.util

import android.content.Context
import android.telecom.TelecomManager

/**
 * Apps STILL never blocks, so a block can't lock you out of calling for help or of the
 * settings needed to undo things: the phone app, Settings, and STILL itself.
 */
class EssentialApps(context: Context) {

    private val appContext = context.applicationContext
    private val telecom = appContext.getSystemService(TelecomManager::class.java)

    fun isEssential(packageName: String): Boolean =
        packageName == appContext.packageName ||
            packageName == SETTINGS_PACKAGE ||
            packageName == telecom?.defaultDialerPackage

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
